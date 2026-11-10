package com.olivamore.api.production.batch;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.production.batch.BatchQueries.LineProductRow;
import com.olivamore.api.production.batch.BatchQueries.ProductType;
import com.olivamore.api.production.batchprocess.BatchProcess;
import com.olivamore.api.production.batchprocess.BatchProcessRepository;
import com.olivamore.api.production.batchprocess.ProcessStatus;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Partiler. Girdiler hattin kabul ettigi kategori/cesitlerden, ayni "type"ta olmali; ciktilar hattin cikti
 * urunlerinden girdinin cesidine gore cozulur; hattin her asamasi icin bekleyen bir proses acilir.
 */
@Service
class ProductionBatchService {

	private static final String INPUT_TYPES = "input_production_product_type_ids";

	private final ProductionBatchRepository batches;
	private final BatchInputTypeRepository inputs;
	private final BatchOutputTypeRepository outputs;
	private final BatchProcessRepository processes;
	private final BatchQueries queries;

	ProductionBatchService(ProductionBatchRepository batches, BatchInputTypeRepository inputs,
		BatchOutputTypeRepository outputs, BatchProcessRepository processes, BatchQueries queries) {
		this.batches = batches;
		this.inputs = inputs;
		this.outputs = outputs;
		this.processes = processes;
		this.queries = queries;
	}

	/** @return yeni parti id */
	@Transactional
	long create(CreateProductionBatchRequest request, CurrentUser user) {
		ValidationErrors errors = new ValidationErrors();
		List<Long> inputIds = request.inputProductionProductTypeIds();
		if (batches.existsByBatchCode(request.batchCode())) {
			errors.add("batch_code", MessageKeys.UNIQUE);
		}
		checkInputTypes(errors, inputIds);
		if (!queries.lineExists(request.productionLineId())) {
			errors.add("production_line_id", MessageKeys.EXISTS);
		}
		checkSupplier(errors, request.supplierId(), request.supplierFieldId());
		LocalDateTime receivedAt = date(errors, "received_at", request.receivedAt());
		LocalDateTime startedAt = date(errors, "started_at", request.startedAt());
		LocalDateTime completedAt = date(errors, "completed_at", request.completedAt());
		errors.throwIfAny();

		long lineId = request.productionLineId();
		Map<Long, ProductType> found = queries.productTypes(inputIds);
		List<ProductType> inputTypes = new ArrayList<>(found.values());
		inputTypes.sort(Comparator.comparingInt(t -> inputIds.indexOf(t.id())));
		assertInputProductsAllowed(lineId, inputTypes);
		List<ProductType> outputTypes = outputTypesForLine(lineId, sharedInputType(inputTypes));
		assertFieldBelongsToSupplier(request.supplierId(), request.supplierFieldId());

		ProductionBatch batch = batches.save(new ProductionBatch(request.batchCode(), lineId, request.supplierId(),
			request.supplierFieldId(), user.id(), request.status() == null ? BatchStatus.PENDING : request.status(),
			request.quantityKg(), receivedAt != null ? receivedAt : Timestamps.now(), startedAt, completedAt, request.note()));
		long id = batch.getId();
		for (int i = 0; i < inputTypes.size(); i++) {
			inputs.save(new BatchInputType(id, inputTypes.get(i).id(), i));
		}
		for (int i = 0; i < outputTypes.size(); i++) {
			outputs.save(new BatchOutputType(id, outputTypes.get(i).id(), i));
		}
		for (long stageId : queries.stageIds(lineId)) {
			processes.save(new BatchProcess(id, stageId));
		}
		return id;
	}

	/** Tedarikci degisip tarla verilmediyse eski tarla duser. Yalnizca gonderilen alanlar. */
	@Transactional
	void update(long id, UpdateProductionBatchRequest request) {
		ProductionBatch batch = find(id);
		ValidationErrors errors = new ValidationErrors();
		if (request.batchCode() != null && batches.existsByBatchCodeAndIdNot(request.batchCode(), id)) {
			errors.add("batch_code", MessageKeys.UNIQUE);
		}
		checkSupplier(errors, value(request.supplierId()), value(request.supplierFieldId()));
		Patch<LocalDateTime> receivedAt = patchDate(errors, "received_at", request.receivedAt());
		Patch<LocalDateTime> startedAt = patchDate(errors, "started_at", request.startedAt());
		Patch<LocalDateTime> completedAt = patchDate(errors, "completed_at", request.completedAt());
		errors.throwIfAny();

		if (request.supplierId() != null || request.supplierFieldId() != null) {
			Long current = batch.getSupplierId();
			Long supplierId = request.supplierId() != null ? request.supplierId().value() : current;
			Long fieldId = request.supplierFieldId() != null ? request.supplierFieldId().value()
				: (Objects.equals(supplierId, current) ? batch.getSupplierFieldId() : null);
			assertFieldBelongsToSupplier(supplierId, fieldId);
			batch.changeSupplier(supplierId, fieldId);
		}
		if (request.batchCode() != null) {
			batch.changeBatchCode(request.batchCode());
		}
		if (request.status() != null) {
			batch.changeStatus(request.status());
		}
		Patch.ifPresent(request.quantityKg(), batch::changeQuantityKg);
		Patch.ifPresent(receivedAt, batch::changeReceivedAt);
		Patch.ifPresent(startedAt, batch::changeStartedAt);
		Patch.ifPresent(completedAt, batch::changeCompletedAt);
		Patch.ifPresent(request.note(), batch::changeNote);
	}

	/** Yalnizca hic baslamamis parti silinir (proses, materyal ya da stok hareketi varsa 422). */
	@Transactional
	void delete(long id) {
		ProductionBatch batch = find(id);
		DeletionGuard.of("'" + batch.getBatchCode() + "' partisi")
			.usedBy("başlamış proses", processes.countByProductionBatchIdAndStatusNot(id, ProcessStatus.WAITING))
			.usedBy("materyal", queries.materialCount(id))
			.usedBy("stok hareketi", queries.movementCount(id))
			.check("Başlamış bir parti silinmez; iptal etmek için durumunu cancelled yapın.");
		batches.delete(batch);
	}

	private ProductionBatch find(long id) {
		return batches.findById(id).orElseThrow(() -> new ModelNotFoundException("ProductionBatch", id));
	}

	// ---- dogrulama --------------------------------------------------------

	private void checkInputTypes(ValidationErrors errors, List<Long> ids) {
		for (int i = 0; i < ids.size(); i++) {
			if (ids.get(i) == null) {
				errors.add(INPUT_TYPES + "." + i, MessageKeys.REQUIRED);
			}
		}
		errors.distinct(INPUT_TYPES, null, ids);
		Set<Long> existing = new LinkedHashSet<>(queries.existingProductTypes(ValidationErrors.present(ids)));
		// Tekrar eden id ayrica "gecersiz" sayilmaz
		ids.stream().filter(id -> id != null && ids.indexOf(id) != ids.lastIndexOf(id)).forEach(existing::add);
		errors.exists(INPUT_TYPES, null, ids, existing);
	}

	private void checkSupplier(ValidationErrors errors, Long supplierId, Long fieldId) {
		if (supplierId != null && !queries.supplierExists(supplierId)) {
			errors.add("supplier_id", MessageKeys.EXISTS);
		}
		if (fieldId != null && !queries.fieldExists(fieldId)) {
			errors.add("supplier_field_id", MessageKeys.EXISTS);
		}
	}

	private static Long value(Patch<Long> patch) {
		return patch == null ? null : patch.value();
	}

	private static LocalDateTime date(ValidationErrors errors, String field, String text) {
		if (text == null) {
			return null;
		}
		Optional<LocalDateTime> parsed = IsoDateTimeDeserializer.parse(text);
		if (parsed.isEmpty()) {
			errors.add(field, MessageKeys.DATE);
			return null;
		}
		return parsed.get().withNano(0);
	}

	private static Patch<LocalDateTime> patchDate(ValidationErrors errors, String field, Patch<String> raw) {
		return raw == null ? null : new Patch<>(date(errors, field, raw.value()));
	}

	// ---- kurallar ----------------------------------------------------------

	/** Hat girdiyi kategoriyle (her zaman) ve istege bagli cesitle sinirlar. */
	private void assertInputProductsAllowed(long lineId, List<ProductType> inputTypes) {
		Map<Long, List<Long>> allowed = new LinkedHashMap<>();
		for (LineProductRow row : queries.lineInputs(List.of(lineId))) {
			List<Long> types = allowed.computeIfAbsent(row.productionProductId(), k -> new ArrayList<>());
			if (row.productionProductTypeId() != null) {
				types.add(row.productionProductTypeId());
			}
		}
		for (ProductType type : inputTypes) {
			if (!allowed.containsKey(type.productionProductId())) {
				throw new BusinessRuleException("Seçilen giriş ürün tipi (" + type.name() + ") bu production line için uygun değil.");
			}
			List<Long> types = allowed.get(type.productionProductId());
			if (!types.isEmpty() && !types.contains(type.id())) {
				List<String> names = queries.productTypes(types).values().stream().map(ProductType::name).toList();
				throw new BusinessRuleException("Bu üretim hattı yalnızca " + String.join(", ", names) + " kabul ediyor; "
					+ type.name() + " ile parti açılamaz.");
			}
		}
	}

	/** Girdilerin ortak "type" degeri (kirpilmis; null ile bos metin ayni sayilir). */
	private static String sharedInputType(List<ProductType> inputTypes) {
		Set<String> types = new LinkedHashSet<>();
		for (ProductType t : inputTypes) {
			types.add(t.type() == null ? "" : t.type().strip());
		}
		if (types.size() > 1) {
			throw new BusinessRuleException("Farklı type değerine sahip giriş ürün tipleri aynı partide şu an desteklenmiyor.");
		}
		String first = inputTypes.getFirst().type();
		return first == null ? null : first.strip();
	}

	/** Hattin ciktilari (id sirasiyla): cesit kilitliyse o, degilse girdinin cesidi, yoksa cesitsiz tip. */
	private List<ProductType> outputTypesForLine(long lineId, String resolvedType) {
		List<LineProductRow> rows = new ArrayList<>(queries.lineOutputs(List.of(lineId)));
		rows.sort(Comparator.comparingLong(LineProductRow::id));
		List<ProductType> out = new ArrayList<>();
		for (LineProductRow row : rows) {
			if (row.productionProductTypeId() != null) {
				out.add(queries.productType(row.productionProductTypeId()).orElseThrow(
					() -> new ModelNotFoundException("ProductionProductType", row.productionProductTypeId())));
				continue;
			}
			out.add(outputType(row.productionProductId(), resolvedType));
		}
		return out;
	}

	private ProductType outputType(long productId, String resolvedType) {
		Optional<ProductType> matched = resolvedType == null || resolvedType.isEmpty() ? Optional.empty()
			: queries.productTypeOf(productId, resolvedType);
		return matched.or(() -> queries.untypedProductTypeOf(productId)).orElseThrow(() -> new BusinessRuleException(
			queries.productName(productId).filter(n -> !n.isEmpty()).orElse("çıkış ürün tipi")
				+ " için uygun productionProductType bulunamadı."));
	}

	private void assertFieldBelongsToSupplier(Long supplierId, Long fieldId) {
		if (fieldId == null) {
			return;
		}
		if (supplierId == null) {
			throw new BusinessRuleException("Tarla seçildiyse tedarikçi de seçilmelidir.");
		}
		if (!supplierId.equals(queries.fieldOwner(fieldId).orElse(null))) {
			throw new BusinessRuleException("Seçilen tarla bu tedarikçiye ait değil.");
		}
	}

}
