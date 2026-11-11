package com.olivamore.api.inventory.storagemovement;

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
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tanka giris (acilis, yeni materyal, var olan materyal), cikis, tanklar arasi transfer, sayim duzeltmesi ve
 * tank bakiyesi. Kurallar sirasiyla: miktar, birim, izinli urun/tip, yogunluk, kapasite, tank uyumu (farkli urun/tip,
 * attribute kurallari ve agirlikli ortalama), materyal bolme.
 * <p>
 * Islemdeki tanklar transaction basinda satir kilidiyle kilitlenir (id sirasiyla); ayni tanka eszamanli iki
 * giris kapasiteyi birlikte asamaz. Uretim ayni islemde JDBC ile yazdigi icin her metot sonunda flush yapilir,
 * materyal satiri veritabanindan tekrar okunur ve tank icerigi hesaplari SQL ile yapilir.
 */
@Service
public class StorageMovementService {

	/** materials.quantity 4 basamak: daha kucuk farklar gurultu. */
	private static final BigDecimal QUANTITY_EPSILON = new BigDecimal("0.00005");

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

	@PersistenceContext
	private EntityManager entityManager;

	StorageMovementService(StorageUnitRepository units, StorageUnitBalanceRepository balances,
		StorageUnitAllowance allowance, StorageUnitAttributeRuleRepository rules, MaterialRepository materials,
		MaterialAttributeValueRepository attributeValues, StorageMovementRepository movements, TankQueries queries) {
		this.units = units;
		this.balances = balances;
		this.allowance = allowance;
		this.rules = rules;
		this.materials = materials;
		this.attributeValues = attributeValues;
		this.movements = movements;
		this.queries = queries;
	}

	/** Transfer sonucu. */
	record Transfer(String reference, StorageMovement out, StorageMovement in) {
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

	@Transactional
	StorageMovement outbound(OutboundRequest request) {
		ValidationErrors errors = new ValidationErrors();
		checkUnit(errors, "storage_unit_id", request.storageUnitId());
		checkMaterial(errors, request.materialId());
		checkBatchProcess(errors, request.batchProcessId());
		errors.throwIfAny();
		return outbound(request.storageUnitId(), request.materialId(), request.quantity(), request.batchProcessId(),
			request.note(), null);
	}

	@Transactional
	Transfer transfer(TransferRequest request) {
		ValidationErrors errors = new ValidationErrors();
		checkUnit(errors, "from_storage_unit_id", request.fromStorageUnitId());
		checkUnit(errors, "to_storage_unit_id", request.toStorageUnitId());
		if (request.toStorageUnitId().equals(request.fromStorageUnitId())) {
			errors.add("to_storage_unit_id", MessageKeys.DIFFERENT, "other", "from storage unit id");
		}
		checkMaterial(errors, request.materialId());
		checkBatchProcess(errors, request.batchProcessId());
		errors.throwIfAny();
		return transfer(request.fromStorageUnitId(), request.toStorageUnitId(), request.materialId(), request.quantity(),
			request.batchProcessId(), request.note());
	}

	/** Artis yeni materyal (tip + birim zorunlu), azalis var olan materyalden (material_id zorunlu). */
	@Transactional
	StorageMovement adjustment(AdjustmentRequest request) {
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
		if (!increase && request.materialId() == null) {
			errors.add("material_id", MessageKeys.REQUIRED);
		}
		else if (request.materialId() != null) {
			checkMaterial(errors, request.materialId());
		}
		checkAttributeValues(errors, request.attributeValues());
		errors.throwIfAny();
		BatchProcess batchProcess = resolveBatchProcess(request.batchProcessId());
		if (increase) {
			return receiveNewMaterial(request.storageUnitId(), productType(request.productionProductTypeId()),
				request.quantity(), request.unit().value(), request.attributeValues(), batchProcess, request.note(),
				MovementSource.STORAGE_ADJUSTMENT, MovementType.ADJUSTMENT_IN);
		}
		return adjustmentDecrease(request.storageUnitId(), request.materialId(), request.quantity(), batchProcess,
			request.note());
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
	// Islemler (uretim de kullanir)
	// ======================================================================

	/** Var olan bir materyalin (ya da bir kisminin) tanka alinmasi. */
	@Transactional
	public StorageMovement inbound(long unitId, long materialId, BigDecimal quantity, Long batchProcessId,
		String note) {
		assertPositiveQuantity(quantity);
		StorageUnit unit = lock(unitId).get(unitId);
		Material material = fresh(materialId);
		ProductType type = productType(material.getProductionProductTypeId());
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		String unitName = material.getUnit().value();
		assertProductionProductAllowed(unit, type);
		assertProductionProductTypeAllowed(unit, type);
		assertDensityDefined(type);
		assertCapacityAvailable(unit, quantity, unitName, type);
		assertCompatibleWithTank(unit, material.getId(), type, quantity);
		Material moving = extractPortion(material, quantity, batchProcess);
		moving.moveToStorageUnit(unit.getId());
		StorageMovement movement = createMovement(unit.getId(), moving, batchProcess, null,
			batchProcess != null ? MovementSource.BATCH_PROCESS : MovementSource.MANUAL, MovementType.IN, null, quantity,
			note, null);
		recalculate(unit.getId(), moving);
		flush();
		return movement;
	}

	/** Tanktan cikan miktar tuketilmis sayilir. @param runId dolum kosusu; yoksa null */
	@Transactional
	public StorageMovement outbound(long unitId, long materialId, BigDecimal quantity, Long batchProcessId,
		String note,
		Long runId) {
		assertPositiveQuantity(quantity);
		StorageUnit unit = lock(unitId).get(unitId);
		Material material = fresh(materialId);
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		assertMaterialInsideStorage(unit, material);
		Material moving = extractPortion(material, quantity, batchProcess);
		moving.close(MaterialStatus.CONSUMED, batchProcess == null ? null : batchProcess.id());
		StorageMovement movement = createMovement(unit.getId(), moving, batchProcess, null,
			batchProcess != null ? MovementSource.BATCH_PROCESS : MovementSource.MANUAL, MovementType.OUT, null, quantity,
			note, runId);
		recalculate(unit.getId(), moving);
		flush();
		return movement;
	}

	@Transactional
	public Transfer transfer(long fromId, long toId, long materialId, BigDecimal quantity, Long batchProcessId,
		String note) {
		assertPositiveQuantity(quantity);
		if (fromId == toId) {
			throw new BusinessRuleException("Aynı tanktan aynı tanka transfer yapılamaz.");
		}
		Map<Long, StorageUnit> locked = lock(fromId, toId);
		StorageUnit from = locked.get(fromId);
		StorageUnit to = locked.get(toId);
		if (from.getKind() == StorageUnitKind.FINAL && to.getKind() == StorageUnitKind.PRE_SETTLING) {
			throw new BusinessRuleException("Son tanktan ön dinlendirme tankına transfer yapılamaz.");
		}
		Material material = fresh(materialId);
		BatchProcess batchProcess = resolveBatchProcess(batchProcessId);
		ProductType type = productType(material.getProductionProductTypeId());
		String unitName = material.getUnit().value();
		assertMaterialInsideStorage(from, material);
		assertProductionProductAllowed(to, type);
		assertProductionProductTypeAllowed(to, type);
		assertDensityDefined(type);
		assertCapacityAvailable(to, quantity, unitName, type);
		assertCompatibleWithTank(to, material.getId(), type, quantity);
		Material moving = extractPortion(material, quantity, batchProcess);
		String reference = "TRF-" + REFERENCE_TIME.format(Timestamps.now()) + "-" + random(6).toUpperCase(Locale.ROOT);
		StorageMovement out = createMovement(from.getId(), moving, batchProcess, to.getId(),
			MovementSource.INTERNAL_TRANSFER, MovementType.TRANSFER_OUT, reference, quantity, note, null);
		moving.moveToStorageUnit(to.getId());
		StorageMovement in = createMovement(to.getId(), moving, batchProcess, from.getId(),
			MovementSource.INTERNAL_TRANSFER, MovementType.TRANSFER_IN, reference, quantity, note, null);
		recalculate(from.getId(), moving);
		recalculate(to.getId(), moving);
		flush();
		return new Transfer(reference, out, in);
	}

	/** Sayim duzeltmesiyle dusulen miktar zayi. */
	private StorageMovement adjustmentDecrease(long unitId, long materialId, BigDecimal quantity,
		BatchProcess batchProcess, String note) {
		assertPositiveQuantity(quantity);
		StorageUnit unit = lock(unitId).get(unitId);
		Material material = fresh(materialId);
		assertMaterialInsideStorage(unit, material);
		Material moving = extractPortion(material, quantity, batchProcess);
		moving.close(MaterialStatus.DISCARDED, batchProcess == null ? null : batchProcess.id());
		StorageMovement movement = createMovement(unit.getId(), moving, batchProcess, null,
			MovementSource.STORAGE_ADJUSTMENT, MovementType.ADJUSTMENT_OUT, null, quantity, note, null);
		recalculate(unit.getId(), moving);
		flush();
		return movement;
	}

	/**
	 * Acilis, yeni materyal girisi ve duzeltme artisi: materyal konumsuz yaratilir, attribute degerleri yazilir,
	 * tank kurallari dogrulanir, sonra tanka yerlestirilir (hata olursa hicbir kayit kalmaz).
	 */
	private StorageMovement receiveNewMaterial(long unitId, ProductType type, BigDecimal quantity, String unitName,
		List<AttributeValueInput> items, BatchProcess batchProcess, String note, MovementSource source,
		MovementType movementType) {
		assertPositiveQuantity(quantity);
		if (!Objects.equals(type.unit(), unitName)) {
			throw new BusinessRuleException("Ürün birimi ile işlem birimi uyuşmuyor.");
		}
		StorageUnit unit = lock(unitId).get(unitId);
		assertProductionProductAllowed(unit, type);
		assertProductionProductTypeAllowed(unit, type);
		assertDensityDefined(type);
		assertCapacityAvailable(unit, quantity, unitName, type);

		Material material = materials.save(new Material(batchProcess == null ? null : batchProcess.productionBatchId(),
			type.id(), source.origin(), quantity, MaterialUnit.of(unitName),
			batchProcess == null ? null : batchProcess.productionStageId(),
			batchProcess == null ? null : batchProcess.id(), note));
		applyAttributeValues(material.getId(), type, items);

		assertCompatibleWithTank(unit, material.getId(), type, quantity);
		material.moveToStorageUnit(unit.getId());
		StorageMovement movement = createMovement(unit.getId(), material, batchProcess, null, source, movementType, null,
			quantity, note, null);
		recalculate(unit.getId(), material);
		flush();
		return movement;
	}

	// ======================================================================
	// Kurallar
	// ======================================================================

	private static void assertPositiveQuantity(BigDecimal quantity) {
		if (quantity.signum() <= 0) {
			throw new BusinessRuleException("Miktar sıfırdan büyük olmalıdır.");
		}
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
		if (material.getStatus() != MaterialStatus.ACTIVE) {
			throw new BusinessRuleException("Sadece aktif material üzerinde işlem yapılabilir.");
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

	/**
	 * Giren miktar ve tanktaki her aktif materyal, kendi tipinin yogunluguyla tankin kapasite birimine cevrilip
	 * toplanir; cevrim gerekip yogunluk yoksa islem reddedilir.
	 */
	private void assertCapacityAvailable(StorageUnit unit, BigDecimal incoming, String unitName, ProductType type) {
		String capacityUnit = unit.getCapacityUnit().value();
		List<Content> contents = queries.contents(unit.getId());
		Map<Long, ProductType> types = queries.productTypes(
			contents.stream().map(Content::productionProductTypeId).toList());
		BigDecimal total = toUnit(incoming, unitName, capacityUnit, type);
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

	private StorageMovement createMovement(long unitId, Material material, BatchProcess batchProcess,
		Long counterpartyId,
		MovementSource source, MovementType type, String reference, BigDecimal quantity, String note, Long runId) {
		return movements.save(new StorageMovement(unitId, material.getId(),
			batchProcess == null ? null : batchProcess.id(),
			runId, counterpartyId, source, type, reference, Quantities.balance(quantity), material.getUnit(), note,
			Timestamps.now()));
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

	/** Harf ve rakam. */
	private static String random(int length) {
		StringBuilder out = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			out.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
		}
		return out.toString();
	}

}
