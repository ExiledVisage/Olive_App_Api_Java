package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.MixShares;
import com.olivamore.api.inventory.PreSettlingTankEmptied;
import com.olivamore.api.inventory.material.LocationType;
import com.olivamore.api.inventory.material.Material;
import com.olivamore.api.inventory.material.MaterialAttributeValue;
import com.olivamore.api.inventory.material.MaterialAttributeValueRepository;
import com.olivamore.api.inventory.material.MaterialRepository;
import com.olivamore.api.inventory.material.MaterialStatus;
import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.inventory.material.Quantities;
import com.olivamore.api.inventory.storagemovement.TankQueries.BatchOutput;
import com.olivamore.api.inventory.storagemovement.TankQueries.BatchProcess;
import com.olivamore.api.inventory.storagemovement.TankQueries.Content;
import com.olivamore.api.inventory.storagemovement.TankQueries.DecimalValue;
import com.olivamore.api.inventory.storagemovement.TankQueries.ProductType;
import com.olivamore.api.inventory.storagemovement.TankQueries.RuleDefinition;
import com.olivamore.api.inventory.storagemovement.TankQueries.TankMaterial;
import com.olivamore.api.inventory.storageunit.ApplyMode;
import com.olivamore.api.inventory.storageunit.StorageUnit;
import com.olivamore.api.inventory.storageunit.StorageUnitAllowance;
import com.olivamore.api.inventory.storageunit.StorageUnitAttributeRule;
import com.olivamore.api.inventory.storageunit.StorageUnitAttributeRuleRepository;
import com.olivamore.api.inventory.storageunit.StorageUnitBalance;
import com.olivamore.api.inventory.storageunit.StorageUnitBalanceRepository;
import com.olivamore.api.inventory.storageunit.StorageUnitKind;
import com.olivamore.api.inventory.storageunit.StorageUnitRepository;
import com.olivamore.api.product.UnitConversion;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tanka giris (acilis, yeni materyal, var olan materyal), cikis, tanklar arasi transfer, sayim duzeltmesi ve
 * tank bakiyesi. Kurallar sirasiyla: miktar, birim, izinli urun/tip, yogunluk, kapasite, zorunlu attribute'lar
 * (son tank), tank uyumu (farkli urun/tip, attribute kurallari ve agirlikli ortalama).
 * <p>
 * Son tank (C2): tank ve urun tipi basina tek tank materyali. Giren materyal giren miktar kadar o anda kapanir,
 * miktar tipin tank materyaline eklenir (attribute'lar {@link TankMixAttributes}); giris hareketi tank materyalini
 * ve gelen materyali gosterir. Cikista cocuk materyal acilmaz: tank sayfasindaki cikis, transfer ve sayim azalisi
 * karisimdan oransal duser, uretim ve dolum verilen materyalden dogrudan duser.
 * <p>
 * On dinlendirme tanki (C3-C5): parti materyalleri ayri durur. Giris yalnizca uretimdeki on dinlendirme asamasiyla
 * (parti bir tanka bir kez girer), cikis yalnizca Boşalt ile: girdiler kapanir, cekilen miktar son tanklara girer,
 * kalan fark (kutle) tortu olarak uretime bildirilir ({@link PreSettlingTankEmptied}). Elle giris, cikis, sayim ve
 * transfer yapilamaz; yanlis giris bosaltmadan once asama iptaliyle geri alinir.
 * <p>
 * Islemdeki tanklar transaction basinda satir kilidiyle kilitlenir (id sirasiyla); ayni tanka eszamanli iki
 * giris kapasiteyi birlikte asamaz. Uretim ayni islemde JDBC ile yazdigi icin her metot sonunda flush yapilir,
 * materyal satiri veritabanindan tekrar okunur ve tank icerigi hesaplari SQL ile yapilir.
 */
@Service
public class StorageMovementService {

	/** materials.quantity 4 basamak: daha kucuk farklar gurultu. */
	private static final BigDecimal QUANTITY_EPSILON = new BigDecimal("0.00005");

	/** Bosaltmada cekilen, giren kutleyi en fazla bu oranda (olcum farki) asabilir; fazlasi reddedilir. */
	private static final BigDecimal EMPTYING_TOLERANCE = new BigDecimal("0.005");

	private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final DateTimeFormatter REFERENCE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final StorageUnitRepository units;
	private final StorageUnitBalanceRepository balances;
	private final StorageUnitAllowance allowance;
	private final StorageUnitAttributeRuleRepository rules;
	private final MaterialRepository materials;
	private final MaterialAttributeValueRepository attributeValues;
	private final StorageMovementRepository movements;
	private final TankQueries queries;
	private final TankMixAttributes mixAttributes;
	private final ApplicationEventPublisher events;

	@PersistenceContext
	private EntityManager entityManager;

	StorageMovementService(StorageUnitRepository units, StorageUnitBalanceRepository balances,
		StorageUnitAllowance allowance, StorageUnitAttributeRuleRepository rules, MaterialRepository materials,
		MaterialAttributeValueRepository attributeValues, StorageMovementRepository movements, TankQueries queries,
		TankMixAttributes mixAttributes, ApplicationEventPublisher events) {
		this.units = units;
		this.balances = balances;
		this.allowance = allowance;
		this.rules = rules;
		this.materials = materials;
		this.attributeValues = attributeValues;
		this.movements = movements;
		this.queries = queries;
		this.mixAttributes = mixAttributes;
		this.events = events;
	}

	/** Transfer sonucu: son tanktan cikista karisimdaki her tip icin bir cikis ve bir giris. */
	record Transfer(String reference, List<StorageMovement> out, List<StorageMovement> in) {
	}

	/** Bosaltma sonucu: her hedef ve parti materyali icin bir cikis ve bir giris; acilan tortu materyalleri. */
	record Emptying(String reference, List<StorageMovement> out, List<StorageMovement> in, List<Long> residueIds) {
	}

	/** Karisimdan cekilecek pay, materyalin biriminde. */
	private record Share(Material material, ProductType type, BigDecimal quantity) {
	}

	// ======================================================================
	// Tank sayfasi
	// ======================================================================

	@Transactional
	StorageMovement opening(OpeningRequest request) {
		ValidationErrors errors = new ValidationErrors();
		checkUnit(errors, "storage_unit_id", request.storageUnitId());
		checkProductType(errors, request.productionProductTypeId());
		checkAttributeValues(errors, request.attributeValues());
		errors.throwIfAny();
		return receiveNewMaterial(request.storageUnitId(), productType(request.productionProductTypeId()),
			request.quantity(), request.unit().value(), request.attributeValues(), null, request.note(),
			MovementSource.STORAGE_OPENING, MovementType.OPENING);
	}

	/** {@code material_id} ile var olan materyal, {@code production_product_type_id} ile yeni materyal. */
	@Transactional
	StorageMovement inbound(InboundRequest request) {
		ValidationErrors errors = new ValidationErrors();
		checkUnit(errors, "storage_unit_id", request.storageUnitId());
		checkInboundSource(errors, request);
		if (request.materialId() != null) {
			checkMaterial(errors, request.materialId());
		}
		if (request.productionProductTypeId() != null) {
			checkProductType(errors, request.productionProductTypeId());
		}
		checkAttributeValues(errors, request.attributeValues());
		checkBatchProcess(errors, request.batchProcessId());
		errors.throwIfAny();
		if (request.materialId() != null) {
			return inbound(request.storageUnitId(), request.materialId(), request.quantity(), request.batchProcessId(),
				request.note());
		}
		ProductType type = productType(request.productionProductTypeId());
		BatchProcess batchProcess = resolveBatchProcess(request.batchProcessId());
		if (batchProcess != null) {
			assertTypeIsBatchOutput(batchProcess, type);
		}
		return receiveNewMaterial(request.storageUnitId(), type, request.quantity(),
			request.unit() == null ? type.unit() : request.unit().value(), request.attributeValues(), batchProcess,
			request.note(), batchProcess != null ? MovementSource.BATCH_PROCESS : MovementSource.MANUAL, MovementType.IN);
	}

	/** Karisimdan oransal: karisimdaki her materyal icin bir hareket. */
	@Transactional
	List<StorageMovement> outbound(OutboundRequest request) {
		ValidationErrors errors = new ValidationErrors();
		checkUnit(errors, "storage_unit_id", request.storageUnitId());
		checkMixMaterial(errors, request.materialId());
		checkBatchProcess(errors, request.batchProcessId());
		errors.throwIfAny();
		BatchProcess batchProcess = resolveBatchProcess(request.batchProcessId());
		return drainMix(request.storageUnitId(), request.materialId(), request.quantity(), batchProcess,
			request.note(), batchProcess != null ? MovementSource.BATCH_PROCESS : MovementSource.MANUAL,
			MovementType.OUT, MaterialStatus.CONSUMED);
	}

	@Transactional
	Transfer transfer(TransferRequest request) {
		ValidationErrors errors = new ValidationErrors();
		checkUnit(errors, "from_storage_unit_id", request.fromStorageUnitId());
		checkUnit(errors, "to_storage_unit_id", request.toStorageUnitId());
		if (request.toStorageUnitId().equals(request.fromStorageUnitId())) {
			errors.add("to_storage_unit_id", MessageKeys.DIFFERENT, "other", "from storage unit id");
		}
		checkMixMaterial(errors, request.materialId());
		checkBatchProcess(errors, request.batchProcessId());
		errors.throwIfAny();
		return transferMix(request.fromStorageUnitId(), request.toStorageUnitId(), request.materialId(),
			request.quantity(), resolveBatchProcess(request.batchProcessId()), request.note());
	}

	/**
	 * On dinlendirme tankinin bosaltilmasi: hedef tanklarin her biri ayri satir ve dogrulama yolu
	 * ({@code targets.N.storage_unit_id}); tank bulunamazsa 404.
	 */
	@Transactional
	Emptying empty(long unitId, EmptyRequest request) {
		if (!queries.unitExists(unitId)) {
			throw new ModelNotFoundException("StorageUnit", unitId);
		}
		ValidationErrors errors = new ValidationErrors();
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < request.targets().size(); i++) {
			Long targetId = request.targets().get(i).storageUnitId();
			String path = "targets." + i + ".storage_unit_id";
			checkUnit(errors, path, targetId);
			if (!seen.add(targetId)) {
				errors.add(path, MessageKeys.DISTINCT);
			}
		}
		errors.throwIfAny();
		return empty(unitId, request.targets(), request.note());
	}

	/**
	 * Artis yeni materyal (tip + birim zorunlu); azalis karisimdan oransal (material_id istege bagli).
	 */
	@Transactional
	List<StorageMovement> adjustment(AdjustmentRequest request) {
		ValidationErrors errors = new ValidationErrors();
		checkUnit(errors, "storage_unit_id", request.storageUnitId());
		checkBatchProcess(errors, request.batchProcessId());
		boolean increase = request.isIncrease();
		if (increase && request.productionProductTypeId() == null) {
			errors.add("production_product_type_id", MessageKeys.REQUIRED);
		}
		else if (request.productionProductTypeId() != null) {
			checkProductType(errors, request.productionProductTypeId());
		}
		if (increase && request.unit() == null) {
			errors.add("unit", MessageKeys.REQUIRED);
		}
		if (!increase) {
			checkMixMaterial(errors, request.materialId());
		}
		else if (request.materialId() != null) {
			checkMaterial(errors, request.materialId());
		}
		checkAttributeValues(errors, request.attributeValues());
		errors.throwIfAny();
		BatchProcess batchProcess = resolveBatchProcess(request.batchProcessId());
		if (increase) {
			return List.of(receiveNewMaterial(request.storageUnitId(), productType(request.productionProductTypeId()),
				request.quantity(), request.unit().value(), request.attributeValues(), batchProcess, request.note(),
				MovementSource.STORAGE_ADJUSTMENT, MovementType.ADJUSTMENT_IN));
		}
		return drainMix(request.storageUnitId(), request.materialId(), request.quantity(), batchProcess,
			request.note(), MovementSource.STORAGE_ADJUSTMENT, MovementType.ADJUSTMENT_OUT, MaterialStatus.DISCARDED);
	}

	// ---- dogrulama --------------------------------------------------------

	private void checkUnit(ValidationErrors errors, String field, Long id) {
		if (!queries.unitExists(id)) {
			errors.add(field, MessageKeys.EXISTS);
		}
	}

	private void checkProductType(ValidationErrors errors, Long id) {
		if (!queries.productTypeExists(id)) {
			errors.add("production_product_type_id", MessageKeys.EXISTS);
		}
	}

	private void checkMaterial(ValidationErrors errors, Long id) {
		if (!queries.materialExists(id)) {
			errors.add("material_id", MessageKeys.EXISTS);
		}
	}

	/** Cikis karisimdan oldugu icin materyal istege bagli; verilirse var olmali. */
	private void checkMixMaterial(ValidationErrors errors, Long materialId) {
		if (materialId != null) {
			checkMaterial(errors, materialId);
		}
	}

	private void checkBatchProcess(ValidationErrors errors, Long id) {
		if (id != null && !queries.batchProcessExists(id)) {
			errors.add("batch_process_id", MessageKeys.EXISTS);
		}
	}

	/** {@code required_without} ve {@code prohibits}: ya materyal ya da yeni urun tipi. */
	private void checkInboundSource(ValidationErrors errors, InboundRequest request) {
		boolean hasMaterial = request.materialId() != null;
		boolean hasType = request.productionProductTypeId() != null;
		if (!hasMaterial && !hasType) {
			errors.add("material_id", MessageKeys.REQUIRED_WITHOUT, "other", "production product type id");
			errors.add("production_product_type_id", MessageKeys.REQUIRED_WITHOUT, "other", "material id");
		}
		if (hasMaterial && hasType) {
			errors.add("production_product_type_id", MessageKeys.PROHIBITS, "other", "material id");
		}
		if (hasMaterial && request.unit() != null) {
			errors.add("unit", MessageKeys.PROHIBITS, "other", "material id");
		}
		if (hasMaterial && request.attributeValues() != null && !request.attributeValues().isEmpty()) {
			errors.add("attribute_values", MessageKeys.PROHIBITS, "other", "material id");
		}
	}

	/** Attribute degerlerindeki tanimlar var mi; {@code value_json} dizi ya da nesne mi. */
	private void checkAttributeValues(ValidationErrors errors, List<AttributeValueInput> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		List<Long> ids = items.stream().map(AttributeValueInput::productionProductAttributeDefinitionId).toList();
		errors.exists("attribute_values", "production_product_attribute_definition_id", ids,
			queries.existingDefinitions(ids));
		for (int i = 0; i < items.size(); i++) {
			JsonNode json = items.get(i).valueJson();
			if (json != null && !json.isNull() && !json.isArray() && !json.isObject()) {
				errors.add("attribute_values." + i + ".value_json", MessageKeys.ARRAY);
			}
		}
	}

	// ======================================================================
	// Islemler (uretim ve dolum da kullanir)
	// ======================================================================

	/**
	 * Partideki bir materyalin (ya da bir kisminin) son tanka alinmasi: tipin tank materyaline eklenir. Tanktaki
	 * materyal bu yoldan cikamaz (son tanktan transfer, on dinlendirme tankindan Boşalt).
	 */
	@Transactional
	public StorageMovement inbound(long unitId, long materialId, BigDecimal quantity, Long batchProcessId,
		String note) {
		assertPositiveQuantity(quantity);
		StorageUnit unit = lock(unitId).get(unitId);
		assertFinal(unit);
		Material material = fresh(materialId);
		assertActive(material);
		assertNotInTank(material);
		ProductType type = productType(material.getProductionProductTypeId());
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		MovementSource source = batchProcess != null ? MovementSource.BATCH_PROCESS : MovementSource.MANUAL;
		assertAccepts(unit, type, quantity, material.getUnit().value());
		assertFits(unit, material, type, quantity);
		assertEnough(material, quantity);
		StorageMovement movement = enterMix(unit, material, quantity, type, batchProcess, null, source,
			MovementType.IN, null, note);
		material.drain(quantity, MaterialStatus.CONSUMED, batchProcessId);
		flush();
		return movement;
	}

	/**
	 * Verilen tank materyalinden cikis, tuketilmis sayilir; materyal dogrudan azalir. Karisimin partisi olmadigindan
	 * uretim icinden tank materyali uzerinde cikis yapilamaz. @param runId dolum kosusu; yoksa null
	 */
	@Transactional
	public StorageMovement outbound(long unitId, long materialId, BigDecimal quantity, Long batchProcessId,
		String note, Long runId) {
		assertPositiveQuantity(quantity);
		StorageUnit unit = lock(unitId).get(unitId);
		assertFinal(unit);
		Material material = fresh(materialId);
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		MovementSource source = batchProcess != null ? MovementSource.BATCH_PROCESS : MovementSource.MANUAL;
		assertMaterialInsideStorage(unit, material);
		assertNotMixFromProduction(material, batchProcess);
		assertEnough(material, quantity);
		material.drain(quantity, MaterialStatus.CONSUMED, batchProcessId);
		StorageMovement movement = createMovement(unit.getId(), material, null, batchProcess, null, source,
			MovementType.OUT, null, quantity, note, runId);
		recalculate(unit.getId(), material);
		flush();
		return movement;
	}

	/** Verilen tank materyalinin (ya da bir kisminin) son tanklar arasinda transferi. */
	@Transactional
	public Transfer transfer(long fromId, long toId, long materialId, BigDecimal quantity, Long batchProcessId,
		String note) {
		assertPositiveQuantity(quantity);
		Map<Long, StorageUnit> locked = lockForTransfer(fromId, toId);
		StorageUnit from = locked.get(fromId);
		StorageUnit to = locked.get(toId);
		Material material = fresh(materialId);
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		ProductType type = productType(material.getProductionProductTypeId());
		assertMaterialInsideStorage(from, material);
		assertNotMixFromProduction(material, batchProcess);
		assertAccepts(to, type, quantity, material.getUnit().value());
		assertFits(to, material, type, quantity);
		assertEnough(material, quantity);
		String reference = reference();
		StorageMovement out = createMovement(from.getId(), material, null, batchProcess, to.getId(),
			MovementSource.INTERNAL_TRANSFER, MovementType.TRANSFER_OUT, reference, quantity, note, null);
		StorageMovement in = enterMix(to, material, quantity, type, batchProcess, from.getId(),
			MovementSource.INTERNAL_TRANSFER, MovementType.TRANSFER_IN, reference, note);
		material.drain(quantity, MaterialStatus.CONSUMED, batchProcessId);
		recalculate(from.getId(), material);
		flush();
		return new Transfer(reference, List.of(out), List.of(in));
	}

	// ======================================================================
	// On dinlendirme tanki
	// ======================================================================

	/**
	 * On dinlendirme asamasinin girisi: partideki materyalin (ya da bir kisminin) tanka alinmasi. Parti materyali
	 * ayri satir olarak kalir; parti ayni tanka bir kez girer. Tek urun tipi kurali tankin ayarindan (coklu tip bu
	 * turde kapali).
	 */
	@Transactional
	public StorageMovement enterPreSettling(long unitId, long materialId, BigDecimal quantity, long batchProcessId,
		String note) {
		assertPositiveQuantity(quantity);
		StorageUnit unit = lock(unitId).get(unitId);
		if (isFinal(unit)) {
			throw new BusinessRuleException("'" + unit.getCode() + "' bir son tank; ön dinlendirme aşaması yalnızca "
				+ "ön dinlendirme tankına giriş yapar.");
		}
		Material material = fresh(materialId);
		assertActive(material);
		assertNotInTank(material);
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		if (queries.batchInTank(unitId, batchProcess.productionBatchId())) {
			String code = queries.batchCode(batchProcess.productionBatchId()).orElse("");
			throw new BusinessRuleException("'" + code + "' partisi bu ön dinlendirme tankına zaten girdi. Miktar "
				+ "yanlışsa aşamayı iptal edip yeniden gönderin.");
		}
		ProductType type = productType(material.getProductionProductTypeId());
		assertAccepts(unit, type, quantity, material.getUnit().value());
		assertFits(unit, material, type, quantity);
		Material moving = extractPortion(material, quantity, batchProcess);
		moving.moveToStorageUnit(unit.getId());
		StorageMovement movement = createMovement(unit.getId(), moving, null, batchProcess, null,
			MovementSource.BATCH_PROCESS, MovementType.IN, null, quantity, note, null);
		recalculate(unit.getId(), moving);
		flush();
		return movement;
	}

	/**
	 * Dinlenmedeki asamanin iptali: prosesin hala tankta duran materyalleri partiye doner (cikis hareketiyle).
	 * Girerken bolunmus parca partideki materyale geri eklenir.
	 */
	@Transactional
	public void cancelPreSettling(long batchProcessId, String note) {
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		for (TankQueries.PreSettlingEntry entry : queries.preSettlingEntries(batchProcessId)) {
			StorageUnit unit = lock(entry.storageUnitId()).get(entry.storageUnitId());
			Material material = fresh(entry.materialId());
			if (material.getStatus() != MaterialStatus.ACTIVE || !Objects.equals(tankOf(material), unit.getId())) {
				continue;
			}
			createMovement(unit.getId(), material, null, batchProcess, null, MovementSource.BATCH_PROCESS,
				MovementType.OUT, null, material.getQuantity(), note, null);
			returnToBatch(material, batchProcessId);
			recalculate(unit.getId(), material);
		}
		flush();
	}

	/**
	 * Boşalt: girdiler %100 kapanir, cekilen miktar hedef son tanklarin tip materyaline eklenir, fark (kutle) tortu
	 * olur. Cekilen (kapasite biriminde) giren materyallere miktarlarina gore oransal dagilir, her hedefe istenen
	 * paylarla. Cekilen giren kutleyi toleranstan fazla asarsa reddedilir; tolerans icindeyse girenin tamami cekilmis
	 * sayilir. Kurallar (izinli tip, yogunluk, kapasite, olculmus olma, tank uyumu) her hedef icin; biri reddederse
	 * hicbir sey yazilmaz.
	 */
	private Emptying empty(long unitId, List<EmptyRequest.Target> targets, String note) {
		long[] ids = new long[targets.size() + 1];
		ids[0] = unitId;
		for (int t = 0; t < targets.size(); t++) {
			ids[t + 1] = targets.get(t).storageUnitId();
		}
		Map<Long, StorageUnit> locked = lock(ids);
		StorageUnit source = locked.get(unitId);
		if (isFinal(source)) {
			throw new BusinessRuleException("'" + source.getCode() + "' bir son tank; Boşalt yalnızca ön dinlendirme "
				+ "tankında yapılır.");
		}
		List<StorageUnit> destinations = targets.stream().map(t -> locked.get(t.storageUnitId())).toList();
		if (destinations.stream().anyMatch(d -> !isFinal(d))) {
			throw new BusinessRuleException("Ön dinlendirme tankı yalnızca son tanklara boşaltılır.");
		}
		List<Material> contents = queries.activeMaterialIds(unitId).stream().map(this::fresh).toList();
		if (contents.isEmpty()) {
			throw new BusinessRuleException("Tank boş; boşaltılacak materyal yok.");
		}
		Map<Long, ProductType> types = queries.productTypes(
			contents.stream().map(Material::getProductionProductTypeId).toList());
		List<MixShares.Part> parts = contents.stream().map(m -> {
			ProductType type = types.get(m.getProductionProductTypeId());
			return new MixShares.Part(m.getQuantity(), m.getUnit().value(), type.density(), type.name());
		}).toList();
		String capacityUnit = source.getCapacityUnit().value();
		List<BigDecimal> requested = targets.stream().map(EmptyRequest.Target::quantity).toList();
		List<BigDecimal> taken = withdrawals(parts, requested, capacityUnit);
		List<List<BigDecimal>> shares = distribute(taken, requested);
		for (int t = 0; t < destinations.size(); t++) {
			assertAcceptsShares(destinations.get(t), contents, types, shares.get(t));
		}

		Map<Long, Long> processes = restingProcesses(unitId, contents);
		String reference = reference();
		List<StorageMovement> out = new ArrayList<>();
		List<StorageMovement> in = new ArrayList<>();
		for (int t = 0; t < destinations.size(); t++) {
			StorageUnit target = destinations.get(t);
			for (int i = 0; i < contents.size(); i++) {
				BigDecimal share = shares.get(t).get(i);
				if (share.signum() <= 0) {
					continue;
				}
				Material material = contents.get(i);
				ProductType type = types.get(material.getProductionProductTypeId());
				BatchProcess process = resolveBatchProcess(processes.get(material.getId()));
				assertFits(target, material, type, share);
				out.add(createMovement(unitId, material, null, process, target.getId(), MovementSource.INTERNAL_TRANSFER,
					MovementType.TRANSFER_OUT, reference, share, note, null));
				in.add(enterMix(target, material, share, type, process, unitId, MovementSource.INTERNAL_TRANSFER,
					MovementType.TRANSFER_IN, reference, note));
			}
		}

		List<PreSettlingTankEmptied.Entry> entries = new ArrayList<>();
		for (int i = 0; i < contents.size(); i++) {
			Material material = contents.get(i);
			Long processId = processes.get(material.getId());
			MixShares.Part part = parts.get(i);
			BigDecimal residue = material.getQuantity().subtract(taken.get(i)).max(BigDecimal.ZERO);
			material.close(MaterialStatus.CONSUMED, processId);
			recalculate(unitId, material);
			if (processId != null) {
				BigDecimal residueKg = UnitConversion.convert(residue, part.unit(), UnitConversion.KG, part.density(),
					part.typeName());
				entries.add(new PreSettlingTankEmptied.Entry(material.getId(), material.getProductionBatchId(), processId,
					Quantities.material(residueKg)));
			}
		}
		flush();
		events.publishEvent(new PreSettlingTankEmptied(unitId, entries));
		flush();
		List<Long> residueIds = queries.childrenCreatedBy(contents.stream().map(Material::getId).toList(),
			entries.stream().map(PreSettlingTankEmptied.Entry::batchProcessId).distinct().toList());
		return new Emptying(reference, out, in, residueIds);
	}

	/**
	 * Her materyalden cekilen, kendi biriminde: istenen toplam (kapasite biriminde) giren kadar ya da azsa oransal;
	 * fazlaysa kutle farki toleransi asmiyorsa girenin tamami, asiyorsa reddedilir.
	 */
	private static List<BigDecimal> withdrawals(List<MixShares.Part> parts, List<BigDecimal> requested,
		String capacityUnit) {
		BigDecimal total = requested.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal inside = MixShares.total(parts, capacityUnit);
		if (total.compareTo(inside) <= 0) {
			return MixShares.split(parts, total, capacityUnit);
		}
		BigDecimal insideKg = MixShares.total(parts, UnitConversion.KG);
		BigDecimal totalKg = total.multiply(insideKg).divide(inside, MathContext.DECIMAL128);
		if (totalKg.subtract(insideKg).compareTo(insideKg.multiply(EMPTYING_TOLERANCE)) > 0) {
			throw new BusinessRuleException("Çekilen miktar (" + plain(total) + " " + capacityUnit + ") tanktakinden ("
				+ plain(Quantities.balance(inside)) + " " + capacityUnit + ") fazla; fark ölçüm toleransını (%"
				+ plain(EMPTYING_TOLERANCE.movePointRight(2)) + ") aşıyor.");
		}
		return parts.stream().map(MixShares.Part::quantity).toList();
	}

	/** Her materyalden cekilenin hedeflere dagilimi, istenen miktarlarla oransal; yuvarlama farki son hedefe. */
	private static List<List<BigDecimal>> distribute(List<BigDecimal> taken, List<BigDecimal> requested) {
		BigDecimal total = requested.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		List<List<BigDecimal>> shares = new ArrayList<>();
		requested.forEach(r -> shares.add(new ArrayList<>()));
		for (BigDecimal materialTaken : taken) {
			BigDecimal given = BigDecimal.ZERO;
			for (int t = 0; t < requested.size(); t++) {
				BigDecimal share = t < requested.size() - 1
					? Quantities.material(materialTaken.multiply(requested.get(t)).divide(total, MathContext.DECIMAL128))
					: materialTaken.subtract(given);
				given = given.add(share);
				shares.get(t).add(share);
			}
		}
		return shares;
	}

	/** Hedef son tank karisimdaki her tipi kabul etmeli ve paylarin toplami sigmali (kapasite biriminde). */
	private void assertAcceptsShares(StorageUnit target, List<Material> contents, Map<Long, ProductType> types,
		List<BigDecimal> shares) {
		String capacityUnit = target.getCapacityUnit().value();
		BigDecimal incoming = BigDecimal.ZERO;
		for (int i = 0; i < contents.size(); i++) {
			ProductType type = types.get(contents.get(i).getProductionProductTypeId());
			assertProductionProductAllowed(target, type);
			assertProductionProductTypeAllowed(target, type);
			assertDensityDefined(type);
			incoming = incoming.add(toUnit(shares.get(i), contents.get(i).getUnit().value(), capacityUnit, type));
		}
		assertCapacityFor(target, incoming);
	}

	/**
	 * Materyali tanka sokan asama dinlenmedeyse (on dinlendirme asamasiyla girmis) o asama; eski (asamasiz) girisler
	 * haritada yok, tortusuz kapanir.
	 */
	private Map<Long, Long> restingProcesses(long unitId, List<Material> contents) {
		Map<Long, Long> entries = queries.entryProcesses(unitId, contents.stream().map(Material::getId).toList());
		Set<Long> resting = queries.restingProcesses(entries.values());
		Map<Long, Long> processes = new HashMap<>();
		entries.forEach((materialId, processId) -> {
			if (resting.contains(processId)) {
				processes.put(materialId, processId);
			}
		});
		return processes;
	}

	/** Girerken bolunmus parca (partideki materyal hala aktifse) ona geri eklenir; degilse kendisi partiye doner. */
	private void returnToBatch(Material material, long batchProcessId) {
		Material parent = material.getParentMaterialId() == null ? null
			: materials.findById(material.getParentMaterialId()).map(m -> fresh(m.getId())).orElse(null);
		boolean split = Objects.equals(material.getCreatedByBatchProcessId(), batchProcessId) && parent != null
			&& parent.getStatus() == MaterialStatus.ACTIVE && parent.getCurrentLocationType() == LocationType.PRODUCTION_BATCH
			&& Objects.equals(parent.getProductionProductTypeId(), material.getProductionProductTypeId())
			&& parent.getUnit() == material.getUnit();
		if (split) {
			parent.add(material.getQuantity());
			material.close(MaterialStatus.CONSUMED, batchProcessId);
		}
		else {
			material.returnToBatch();
		}
	}

	/**
	 * Son tanktan transfer: karisimdan oransal. Hedef karisimdaki her tipi kabul etmeli; biri reddedilirse
	 * transferin tamami reddedilir (kurallar yazmadan once, uyum kurallari tip tip; hata islemi geri alir).
	 */
	private Transfer transferMix(long fromId, long toId, Long materialId, BigDecimal quantity,
		BatchProcess batchProcess, String note) {
		assertPositiveQuantity(quantity);
		Map<Long, StorageUnit> locked = lockForTransfer(fromId, toId);
		StorageUnit from = locked.get(fromId);
		StorageUnit to = locked.get(toId);
		if (materialId != null) {
			assertMaterialInsideStorage(from, fresh(materialId));
		}
		List<Share> shares = shares(from, quantity);
		String capacityUnit = to.getCapacityUnit().value();
		BigDecimal incoming = BigDecimal.ZERO;
		for (Share share : shares) {
			assertProductionProductAllowed(to, share.type());
			assertProductionProductTypeAllowed(to, share.type());
			assertDensityDefined(share.type());
			incoming = incoming.add(toUnit(share.quantity(), share.material().getUnit().value(), capacityUnit,
				share.type()));
		}
		assertCapacityFor(to, incoming);
		String reference = reference();
		Long processId = batchProcess == null ? null : batchProcess.id();
		List<StorageMovement> out = new ArrayList<>();
		List<StorageMovement> in = new ArrayList<>();
		for (Share share : shares) {
			Material material = share.material();
			assertFits(to, material, share.type(), share.quantity());
			out.add(createMovement(from.getId(), material, null, batchProcess, to.getId(),
				MovementSource.INTERNAL_TRANSFER, MovementType.TRANSFER_OUT, reference, share.quantity(), note, null));
			in.add(enterMix(to, material, share.quantity(), share.type(), batchProcess, from.getId(),
				MovementSource.INTERNAL_TRANSFER, MovementType.TRANSFER_IN, reference, note));
			material.drain(share.quantity(), MaterialStatus.CONSUMED, processId);
			recalculate(from.getId(), material);
		}
		flush();
		return new Transfer(reference, out, in);
	}

	/** Son tanktan cikis ya da sayim azalisi: karisimdan oransal, karisimdaki her materyal icin bir hareket. */
	private List<StorageMovement> drainMix(long unitId, Long materialId, BigDecimal quantity, BatchProcess batchProcess,
		String note, MovementSource source, MovementType movementType, MaterialStatus closedStatus) {
		assertPositiveQuantity(quantity);
		StorageUnit unit = lock(unitId).get(unitId);
		assertFinal(unit);
		if (materialId != null) {
			assertMaterialInsideStorage(unit, fresh(materialId));
		}
		Long processId = batchProcess == null ? null : batchProcess.id();
		List<StorageMovement> written = new ArrayList<>();
		for (Share share : shares(unit, quantity)) {
			share.material().drain(share.quantity(), closedStatus, processId);
			written.add(createMovement(unitId, share.material(), null, batchProcess, null, source, movementType, null,
				share.quantity(), note, null));
			recalculate(unitId, share.material());
		}
		flush();
		return written;
	}

	/**
	 * Acilis, yeni materyal girisi ve duzeltme artisi: materyal konumsuz yaratilir, attribute degerleri yazilir,
	 * tank kurallari dogrulanir, sonra tipin tank materyaline eklenip kapanir (hata olursa hicbir kayit kalmaz).
	 */
	private StorageMovement receiveNewMaterial(long unitId, ProductType type, BigDecimal quantity, String unitName,
		List<AttributeValueInput> items, BatchProcess batchProcess, String note, MovementSource source,
		MovementType movementType) {
		assertPositiveQuantity(quantity);
		if (!Objects.equals(type.unit(), unitName)) {
			throw new BusinessRuleException("Ürün birimi ile işlem birimi uyuşmuyor.");
		}
		StorageUnit unit = lock(unitId).get(unitId);
		assertFinal(unit);
		assertAccepts(unit, type, quantity, unitName);

		Material material = materials.save(new Material(batchProcess == null ? null : batchProcess.productionBatchId(),
			type.id(), source.origin(), quantity, MaterialUnit.of(unitName),
			batchProcess == null ? null : batchProcess.productionStageId(),
			batchProcess == null ? null : batchProcess.id(), note));
		applyAttributeValues(material.getId(), type, items);

		assertFits(unit, material, type, quantity);
		StorageMovement movement = enterMix(unit, material, quantity, type, batchProcess, null, source,
			movementType, null, note);
		material.drain(quantity, MaterialStatus.CONSUMED, batchProcess == null ? null : batchProcess.id());
		flush();
		return movement;
	}

	/**
	 * Gelen materyalden (birimi ne olursa olsun) verilen miktar son tankta tipin tank materyaline eklenir: yoksa
	 * acilir, miktar tipin birimine cevrilir, attribute'lar birlesir. Gelenin azaltilmasi cagirana kalir.
	 * @return giris hareketi: material_id tank materyali, source_material_id gelen
	 */
	private StorageMovement enterMix(StorageUnit unit, Material incoming, BigDecimal quantity, ProductType type,
		BatchProcess batchProcess, Long counterpartyId, MovementSource source, MovementType movementType,
		String reference, String note) {
		Material mix = tankMix(unit, type, incoming.getUnit());
		BigDecimal added = Quantities.material(toUnit(quantity, incoming.getUnit().value(), mix.getUnit().value(),
			type));
		mixAttributes.merge(mix.getId(), mix.getQuantity(), incoming.getId(), added);
		mix.add(added);
		StorageMovement movement = createMovement(unit.getId(), mix, incoming.getId(), batchProcess, counterpartyId,
			source, movementType, reference, added, note, null);
		recalculate(unit.getId(), mix);
		return movement;
	}

	/** Tipin aktif tank materyali; yoksa tipin biriminde (tanimsizsa gelenin biriminde) yeni acilir. */
	private Material tankMix(StorageUnit unit, ProductType type, MaterialUnit fallbackUnit) {
		return queries.tankMix(unit.getId(), type.id()).map(this::fresh).orElseGet(() -> {
			MaterialUnit unitOfType = Objects.requireNonNullElse(MaterialUnit.of(type.unit()), fallbackUnit);
			Material created = materials.save(Material.tankMix(type.id(), unitOfType, unit.getId()));
			flush();
			return created;
		});
	}

	/**
	 * Karisimdan oransal paylar ({@link MixShares}, dolumla ayni kural): istenen miktar tankin kapasite biriminde,
	 * paylar materyallerin kendi biriminde; yuvarlama farki son materyale.
	 */
	private List<Share> shares(StorageUnit unit, BigDecimal requested) {
		List<Material> contents = queries.activeMaterialIds(unit.getId()).stream().map(this::fresh).toList();
		if (contents.isEmpty()) {
			throw new BusinessRuleException("Tank boş; çekilecek materyal yok.");
		}
		Map<Long, ProductType> types = queries.productTypes(
			contents.stream().map(Material::getProductionProductTypeId).toList());
		List<MixShares.Part> parts = contents.stream().map(m -> {
			ProductType type = types.get(m.getProductionProductTypeId());
			return new MixShares.Part(m.getQuantity(), m.getUnit().value(), type == null ? null : type.density(),
				type == null ? null : type.name());
		}).toList();
		String capacityUnit = unit.getCapacityUnit().value();
		if (requested.subtract(MixShares.total(parts, capacityUnit)).compareTo(QUANTITY_EPSILON) > 0) {
			throw new BusinessRuleException("Tankta yeterli miktar bulunmuyor.");
		}
		List<BigDecimal> split = MixShares.split(parts, requested, capacityUnit);
		List<Share> shares = new ArrayList<>();
		for (int i = 0; i < contents.size(); i++) {
			if (split.get(i).signum() > 0) {
				Material m = contents.get(i);
				shares.add(new Share(m, types.get(m.getProductionProductTypeId()), split.get(i)));
			}
		}
		return shares;
	}

	// ======================================================================
	// Kurallar
	// ======================================================================

	private static void assertPositiveQuantity(BigDecimal quantity) {
		if (quantity.signum() <= 0) {
			throw new BusinessRuleException("Miktar sıfırdan büyük olmalıdır.");
		}
	}

	/** Tanka girecek miktar: izinli urun ve tip, yogunluk, kapasite (materyal yaratilmadan once). */
	private void assertAccepts(StorageUnit unit, ProductType type, BigDecimal quantity, String unitName) {
		assertProductionProductAllowed(unit, type);
		assertProductionProductTypeAllowed(unit, type);
		assertDensityDefined(type);
		assertCapacityAvailable(unit, quantity, unitName, type);
	}

	/** Gelen materyalin degerleriyle: son tankta zorunlu attribute'lar, sonra tank uyumu. */
	private void assertFits(StorageUnit unit, Material incoming, ProductType type, BigDecimal quantity) {
		if (isFinal(unit)) {
			assertRequiredAttributes(incoming, type);
		}
		assertCompatibleWithTank(unit, incoming.getId(), type, quantity);
	}

	private void assertTypeIsBatchOutput(BatchProcess batchProcess, ProductType type) {
		List<BatchOutput> outputs = queries.batchOutputs(batchProcess.productionBatchId());
		if (outputs.stream().anyMatch(o -> Objects.equals(o.productionProductTypeId(), type.id()))) {
			return;
		}
		List<String> names = outputs.stream().map(BatchOutput::name)
			.filter(name -> name != null && !name.isEmpty() && !name.equals("0")).toList();
		String code = queries.batchCode(batchProcess.productionBatchId()).orElse("");
		throw new BusinessRuleException("'" + Objects.requireNonNullElse(type.name(), "") + "', '" + code
			+ "' partisinin çıktılarından biri değil. Partinin çıktıları: " + String.join(", ", names) + ".");
	}

	private void assertProductionProductAllowed(StorageUnit unit, ProductType type) {
		if (!allowance.allowsProduct(unit.getId(), type.productionProductId())) {
			throw new BusinessRuleException("Seçilen tank bu ürün tipini kabul etmiyor.");
		}
	}

	private void assertProductionProductTypeAllowed(StorageUnit unit, ProductType type) {
		if (!allowance.allowsProductType(unit.getId(), type.id())) {
			throw new BusinessRuleException("Seçilen tank bu spesifik ürünü kabul etmiyor.");
		}
	}

	private static void assertMaterialInsideStorage(StorageUnit unit, Material material) {
		if (material.getCurrentLocationType() != LocationType.STORAGE_UNIT
			|| !Objects.equals(material.getCurrentLocationId(), unit.getId())) {
			throw new BusinessRuleException("Seçilen material bu tankın içinde bulunmuyor.");
		}
		assertActive(material);
	}

	/** On dinlendirme tankinda elle giris, cikis, sayim ve transfer yoktur. */
	private static void assertFinal(StorageUnit unit) {
		if (!isFinal(unit)) {
			throw new BusinessRuleException("'" + unit.getCode() + "' bir ön dinlendirme tankı; elle giriş, çıkış, sayım "
				+ "ve transfer yapılamaz. Giriş üretimdeki ön dinlendirme aşamasıyla, çıkış Boşalt ile yapılır.");
		}
	}

	/** Tanktaki materyal giris yoluyla baska tanka gecemez: son tanktan transfer, on dinlendirmeden Boşalt. */
	private static void assertNotInTank(Material material) {
		if (tankOf(material) != null) {
			throw new BusinessRuleException("Seçilen material bir tankın içinde; tanktan çıkış transfer ya da "
				+ "Boşalt ile yapılır.");
		}
	}

	private static void assertActive(Material material) {
		if (material.getStatus() != MaterialStatus.ACTIVE) {
			throw new BusinessRuleException("Sadece aktif material üzerinde işlem yapılabilir.");
		}
	}

	private static void assertEnough(Material material, BigDecimal quantity) {
		if (quantity.subtract(material.getQuantity()).compareTo(QUANTITY_EPSILON) > 0) {
			throw new BusinessRuleException("Material üzerinde yeterli miktar bulunmuyor.");
		}
	}

	/** Son tanktaki karisimin partisi yok: uretim icinden uzerinde cikis ya da transfer yapilamaz. */
	private static void assertNotMixFromProduction(Material material, BatchProcess batchProcess) {
		if (batchProcess != null && material.isTankMix()) {
			throw new BusinessRuleException("Son tanktaki karışım materyalinin partisi yok; üretim içinden üzerinde "
				+ "çıkış ya da transfer yapılamaz.");
		}
	}

	/** Fabrikada kg'si bilinmeyen urun tanka konmaz: birim ayni olsa bile tipin etkin yogunlugu tanimli olmali. */
	private static void assertDensityDefined(ProductType type) {
		if (type.density() == null) {
			String name = type.name() != null ? type.name() : "Bu ürün tipi";
			throw new BusinessRuleException(name + " için yoğunluk tanımlı değil; yoğunluğu bilinmeyen ürün tanka "
				+ "giremez. Ürün tipine yoğunluk (kg/L) girilmeli.");
		}
	}

	/** Olculmemis materyal son tanka giremez: kategorinin zorunlu attribute'larinin hepsinde deger olmali. */
	private void assertRequiredAttributes(Material incoming, ProductType type) {
		Map<Long, String> required = queries.requiredDefinitions(type.productionProductId());
		if (required.isEmpty()) {
			return;
		}
		flush();
		Set<Long> present = queries.definitionsWithValue(incoming.getId());
		List<String> missing = required.entrySet().stream().filter(e -> !present.contains(e.getKey()))
			.map(Map.Entry::getValue).toList();
		if (!missing.isEmpty()) {
			throw new BusinessRuleException("Ölçülmemiş materyal son tanka giremez. Eksik zorunlu özellikler: "
				+ String.join(", ", missing) + ".");
		}
	}

	/**
	 * Giren miktar ve tanktaki her aktif materyal, kendi tipinin yogunluguyla tankin kapasite birimine cevrilip
	 * toplanir; cevrim gerekip yogunluk yoksa islem reddedilir.
	 */
	private void assertCapacityAvailable(StorageUnit unit, BigDecimal incoming, String unitName, ProductType type) {
		assertCapacityFor(unit, toUnit(incoming, unitName, unit.getCapacityUnit().value(), type));
	}

	/** @param incoming giren toplam, tankin kapasite biriminde */
	private void assertCapacityFor(StorageUnit unit, BigDecimal incoming) {
		String capacityUnit = unit.getCapacityUnit().value();
		List<Content> contents = queries.contents(unit.getId());
		Map<Long, ProductType> types = queries.productTypes(
			contents.stream().map(Content::productionProductTypeId).toList());
		BigDecimal total = incoming;
		for (Content content : contents) {
			total = total.add(toUnit(content.quantity(), content.unit(), capacityUnit,
				types.get(content.productionProductTypeId())));
		}
		if (Quantities.material(total).compareTo(unit.getCapacity()) > 0) {
			throw new BusinessRuleException("Hedef tankta yeterli boş kapasite yok.");
		}
	}

	private static BigDecimal toUnit(BigDecimal quantity, String from, String to, ProductType type) {
		return UnitConversion.convert(quantity, from, to, type == null ? null : type.density(),
			type == null ? null : type.name());
	}

	/**
	 * Tank bossa yalnizca attribute kurallari; doluysa once farkli urun (kategori) ve farkli tip kurallari.
	 * @param incomingQuantity tanka fiilen girecek miktar (agirlikli ortalamada agirlik)
	 */
	private void assertCompatibleWithTank(StorageUnit unit, long incomingId, ProductType incomingType,
		BigDecimal incomingQuantity) {
		flush();
		List<TankMaterial> existing = queries.activeMaterials(unit.getId());
		List<Long> typeIds = new ArrayList<>(existing.stream().map(TankMaterial::productionProductTypeId).toList());
		typeIds.add(incomingType.id());
		Map<Long, ProductType> types = queries.productTypes(typeIds);
		List<Long> materialIds = new ArrayList<>(existing.stream().map(TankMaterial::id).toList());
		materialIds.add(incomingId);
		Map<Long, List<DecimalValue>> values = queries.decimalValues(materialIds).stream()
			.collect(Collectors.groupingBy(DecimalValue::materialId));
		if (!existing.isEmpty()) {
			if (!unit.isAllowMultipleProducts()
				&& existing.stream().anyMatch(m -> productOf(types, m) != productIdOf(incomingType))) {
				throw new BusinessRuleException("Bu tank doluyken farklı ürün tipleri bir arada tutulamaz.");
			}
			if (!unit.isAllowMultipleTypes()
				&& existing.stream().anyMatch(m -> !Objects.equals(m.productionProductTypeId(), incomingType.id()))) {
				throw new BusinessRuleException("Bu tank doluyken farklı ürünler bir arada tutulamaz.");
			}
		}
		assertAttributeRules(unit, incomingId, incomingType, existing, types, values, incomingQuantity);
	}

	private void assertAttributeRules(StorageUnit unit, long incomingId, ProductType incomingType,
		List<TankMaterial> existing, Map<Long, ProductType> types, Map<Long, List<DecimalValue>> values,
		BigDecimal incomingQuantity) {
		List<StorageUnitAttributeRule> active = rules.findByStorageUnitIdAndActiveTrue(unit.getId());
		if (active.isEmpty()) {
			return;
		}
		Map<Long, RuleDefinition> definitions = queries.ruleDefinitions(
			active.stream().map(StorageUnitAttributeRule::getDefinitionId).toList());
		for (StorageUnitAttributeRule rule : active) {
			RuleDefinition definition = definitions.get(rule.getDefinitionId());
			if (definition == null) {
				continue;
			}
			long productId = definition.productionProductId() == null ? 0 : definition.productionProductId();
			if (productId != productIdOf(incomingType)) {
				continue;
			}
			if (definition.attributeDefinitionId() == null || !"decimal".equals(definition.dataType())) {
				throw new BusinessRuleException("Tank attribute rule şu anda sadece decimal attribute tipleri ile çalışır.");
			}
			BigDecimal incomingValue = decimalValue(values.get(incomingId), definition.id());
			if (incomingValue == null) {
				throw new BusinessRuleException(
					"Gelen material üzerinde tank kuralı için gerekli attribute değeri bulunamadı.");
			}
			String label = definition.label() != null ? definition.label()
				: Objects.requireNonNullElse(definition.attributeKey(), "");
			ApplyMode mode = rule.getApplyMode();
			if (mode == ApplyMode.INCOMING_ONLY || mode == ApplyMode.INCOMING_AND_AVERAGE) {
				assertWithinRange(incomingValue, rule.getMinValueDecimal(), rule.getMaxValueDecimal(), label);
			}
			if (mode == ApplyMode.TANK_AVERAGE_ONLY || mode == ApplyMode.INCOMING_AND_AVERAGE) {
				BigDecimal weighted = BigDecimal.ZERO;
				BigDecimal total = BigDecimal.ZERO;
				for (TankMaterial m : existing) {
					if (productOf(types, m) != productId) {
						continue;
					}
					BigDecimal value = decimalValue(values.get(m.id()), definition.id());
					if (value == null) {
						throw new BusinessRuleException(
							"Tank içindeki mevcut material üzerinde gerekli attribute değeri bulunamadı.");
					}
					weighted = weighted.add(value.multiply(m.quantity()));
					total = total.add(m.quantity());
				}
				weighted = weighted.add(incomingValue.multiply(incomingQuantity));
				total = total.add(incomingQuantity);
				if (total.signum() <= 0) {
					throw new BusinessRuleException("Tank ortalaması hesaplanamadı.");
				}
				assertWithinRange(weighted.divide(total, MathContext.DECIMAL128), rule.getMinValueDecimal(),
					rule.getMaxValueDecimal(), label + " ortalaması");
			}
		}
	}

	/** Ilk (tanima ait, decimal degeri dolu) attribute degeri. */
	private static BigDecimal decimalValue(List<DecimalValue> values, long definitionId) {
		if (values == null) {
			return null;
		}
		return values.stream()
			.filter(v -> Objects.equals(v.definitionId(), definitionId) && v.valueDecimal() != null)
			.map(DecimalValue::valueDecimal).findFirst().orElse(null);
	}

	private static void assertWithinRange(BigDecimal value, BigDecimal min, BigDecimal max, String label) {
		if (min != null && value.compareTo(min) < 0) {
			throw new BusinessRuleException(label + " değeri tank için izin verilen minimum sınırın altında.");
		}
		if (max != null && value.compareTo(max) > 0) {
			throw new BusinessRuleException(label + " değeri tank için izin verilen maksimum sınırın üstünde.");
		}
	}

	// ======================================================================
	// Materyal ve bakiye
	// ======================================================================

	/**
	 * Tamami (epsilon toleransiyla) isteniyorsa materyalin kendisi; degilse ayni ozelliklerde bir cocuk
	 * materyal (attribute degerleri kopyalanir) ve ebeveynin miktari dusulur.
	 */
	private Material extractPortion(Material material, BigDecimal quantity, BatchProcess batchProcess) {
		BigDecimal current = material.getQuantity();
		if (quantity.subtract(current).compareTo(QUANTITY_EPSILON) > 0) {
			throw new BusinessRuleException("Material üzerinde yeterli miktar bulunmuyor.");
		}
		if (current.subtract(quantity).abs().compareTo(QUANTITY_EPSILON) <= 0) {
			return material;
		}
		Material child = materials.save(material.splitOff(quantity, batchProcess == null ? null : batchProcess.id()));
		flush();
		queries.copyAttributeValues(material.getId(), child.getId(), Timestamps.now());
		material.reduceTo(current.subtract(quantity));
		return child;
	}

	/** Gecersiz tanim (kategoride yok) 422; gecerliler sirayla yazilir. */
	private void applyAttributeValues(long materialId, ProductType type, List<AttributeValueInput> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		Set<Long> definitions = queries.definitionsOfProduct(type.productionProductId());
		for (AttributeValueInput item : items) {
			Long definitionId = item.productionProductAttributeDefinitionId();
			if (definitionId == null || !definitions.contains(definitionId)) {
				throw ValidationFailedException.of("attribute_values", "Geçersiz attribute definition gönderildi.");
			}
			attributeValues.save(new MaterialAttributeValue(materialId, definitionId, item.valueBoolean(),
				item.valueInteger(), item.valueDecimal(), item.valueText(), item.valueJson()));
		}
	}

	/**
	 * Tanktaki aktif materyallerin (ayni kategori, ayni birim) toplami; 0 ise bakiye silinir, degilse
	 * guncellenir ya da yaratilir (2 basamakta degismediyse dokunulmaz).
	 */
	private void recalculate(long unitId, Material material) {
		flush();
		Long productId = productType(material.getProductionProductTypeId()).productionProductId();
		MaterialUnit unit = material.getUnit();
		BigDecimal total = queries.materialTotal(unitId, unit.value(), productId);
		if (total.signum() <= 0) {
			balances.deleteForTank(unitId, productId, unit);
			return;
		}
		BigDecimal quantity = Quantities.balance(total);
		StorageUnitBalance balance = balances.find(unitId, productId, unit).orElse(null);
		if (balance == null) {
			balances.save(new StorageUnitBalance(unitId, productId, unit, quantity));
		}
		else {
			balance.setCurrentQuantity(quantity);
		}
	}

	/** @param sourceMaterialId son tanka giriste gelen materyal; yoksa null */
	private StorageMovement createMovement(long unitId, Material material, Long sourceMaterialId,
		BatchProcess batchProcess, Long counterpartyId, MovementSource source, MovementType type, String reference,
		BigDecimal quantity, String note, Long runId) {
		return movements.save(new StorageMovement(unitId, material.getId(), sourceMaterialId,
			batchProcess == null ? null : batchProcess.id(), runId, counterpartyId, source, type, reference,
			Quantities.balance(quantity), material.getUnit(), note, Timestamps.now()));
	}

	// ======================================================================
	// yardimcilar
	// ======================================================================

	/** Islemdeki tanklar (id sirasiyla, kilitlenme olmasin); kilit altinda en guncel halleriyle. */
	private Map<Long, StorageUnit> lock(long... unitIds) {
		List<Long> ids = Arrays.stream(unitIds).sorted().boxed().toList();
		Map<Long, StorageUnit> locked = new LinkedHashMap<>();
		units.findAllForUpdate(ids).forEach(u -> locked.put(u.getId(), u));
		for (Long id : ids) {
			if (!locked.containsKey(id)) {
				throw new ModelNotFoundException("StorageUnit", id);
			}
		}
		return locked;
	}

	/** Ayni tank reddedilir; transfer yalnizca son tanklar arasinda (on dinlendirme tankindan cikis Boşalt). */
	private Map<Long, StorageUnit> lockForTransfer(long fromId, long toId) {
		if (fromId == toId) {
			throw new BusinessRuleException("Aynı tanktan aynı tanka transfer yapılamaz.");
		}
		Map<Long, StorageUnit> locked = lock(fromId, toId);
		assertFinal(locked.get(fromId));
		assertFinal(locked.get(toId));
		return locked;
	}

	private static boolean isFinal(StorageUnit unit) {
		return unit.getKind() == StorageUnitKind.FINAL;
	}

	private static Long tankOf(Material material) {
		return material.getCurrentLocationType() == LocationType.STORAGE_UNIT ? material.getCurrentLocationId() : null;
	}

	/** Uretim ayni islemde JDBC ile yazmis olabilir: materyal her seferinde veritabanindan okunur. */
	private Material fresh(long id) {
		Material material = materials.findById(id).orElseThrow(() -> new ModelNotFoundException("Material", id));
		entityManager.refresh(material);
		return material;
	}

	private void flush() {
		entityManager.flush();
	}

	private ProductType productType(long id) {
		return queries.productType(id).orElseThrow(() -> new ModelNotFoundException("ProductionProductType", id));
	}

	private BatchProcess resolveBatchProcess(Long id) {
		if (id == null || id == 0) {
			return null;
		}
		return queries.batchProcess(id).orElseThrow(() -> new ModelNotFoundException("BatchProcess", id));
	}

	private static long productOf(Map<Long, ProductType> types, TankMaterial material) {
		ProductType type = material.productionProductTypeId() == null ? null
			: types.get(material.productionProductTypeId());
		return type == null ? 0 : productIdOf(type);
	}

	private static long productIdOf(ProductType type) {
		return type.productionProductId() == null ? 0 : type.productionProductId();
	}

	/** Sondaki sifirlar olmadan. */
	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	private static String reference() {
		return "TRF-" + REFERENCE_TIME.format(Timestamps.now()) + "-" + random(6).toUpperCase(Locale.ROOT);
	}

	/** Harf ve rakam. */
	private static String random(int length) {
		StringBuilder out = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			out.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
		}
		return out.toString();
	}

}
