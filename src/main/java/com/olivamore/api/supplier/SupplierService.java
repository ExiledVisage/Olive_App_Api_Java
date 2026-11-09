package com.olivamore.api.supplier;

import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
class SupplierService {

	private static final String USE_PASSIVE = "Pasife alabilirsiniz.";

	private final SupplierRepository suppliers;
	private final SupplierFieldRepository fields;

	SupplierService(SupplierRepository suppliers, SupplierFieldRepository fields) {
		this.suppliers = suppliers;
		this.fields = fields;
	}

	@Transactional(readOnly = true)
	List<SupplierResponse> all(Boolean isActive, String search) {
		Map<Long, Long> counts = suppliers.countBatches().stream().collect(Collectors.toMap(
			SupplierRepository.BatchCount::getSupplierId, SupplierRepository.BatchCount::getTotal));
		return suppliers.findAll(SupplierSpecifications.matching(isActive, search), Sort.by("name")).stream()
			.map(supplier -> SupplierResponse.of(supplier, counts.getOrDefault(supplier.getId(), 0L)))
			.toList();
	}

	@Transactional(readOnly = true)
	SupplierResponse get(long id) {
		return detail(find(id));
	}

	/** Tedarikci ve (varsa) tarlalari birlikte. */
	@Transactional
	SupplierResponse create(CreateSupplierRequest request) {
		assertNewCodes(request);
		return detail(suppliers.save(new Supplier(request)));
	}

	@Transactional
	SupplierResponse update(long id, UpdateSupplierRequest request) {
		Supplier supplier = find(id);
		if (request.code() != null && suppliers.existsByCodeAndIdNot(request.code(), id)) {
			throwCodeTaken("code");
		}
		supplier.apply(request);
		suppliers.flush();
		return detail(supplier);
	}

	@Transactional
	void delete(long id) {
		Supplier supplier = find(id);
		DeletionGuard.of("'" + supplier.getName() + "' tedarikçisi")
			.usedBy("üretim partisi", suppliers.countBatches(id))
			.check(USE_PASSIVE);
		suppliers.delete(supplier);
	}

	@Transactional
	SupplierFieldResponse createField(long supplierId, CreateSupplierFieldRequest request) {
		Supplier supplier = find(supplierId);
		if (fields.existsByCode(request.code())) {
			throwCodeTaken("code");
		}
		return SupplierFieldResponse.of(fields.save(new SupplierField(supplier, request)));
	}

	@Transactional
	SupplierFieldResponse updateField(long supplierId, long fieldId, UpdateSupplierFieldRequest request) {
		SupplierField field = findField(supplierId, fieldId);
		if (request.code() != null && fields.existsByCodeAndIdNot(request.code(), fieldId)) {
			throwCodeTaken("code");
		}
		field.apply(request);
		return SupplierFieldResponse.of(field);
	}

	@Transactional
	void deleteField(long supplierId, long fieldId) {
		SupplierField field = findField(supplierId, fieldId);
		DeletionGuard.of("'" + field.getCode() + "' tarlası")
			.usedBy("üretim partisi", fields.countBatches(fieldId))
			.check(USE_PASSIVE);
		fields.delete(field);
	}

	@Transactional(readOnly = true)
	void ensureExists(long id) {
		if (!suppliers.existsById(id)) {
			throw new ModelNotFoundException("Supplier", id);
		}
	}

	private SupplierResponse detail(Supplier supplier) {
		return SupplierResponse.of(supplier, suppliers.countBatches(supplier.getId()));
	}

	private Supplier find(long id) {
		return suppliers.findById(id).orElseThrow(() -> new ModelNotFoundException("Supplier", id));
	}

	private SupplierField findField(long supplierId, long fieldId) {
		ensureExists(supplierId);
		SupplierField field = fields.findById(fieldId)
			.orElseThrow(() -> new ModelNotFoundException("SupplierField", fieldId));
		if (field.getSupplier().getId() != supplierId) {
			throw new HttpStatusException(404, "Bu tarla bu tedarikçiye ait değil.");
		}
		return field;
	}

	private void assertNewCodes(CreateSupplierRequest request) {
		ValidationErrors errors = new ValidationErrors();
		if (suppliers.existsByCode(request.code())) {
			errors.add("code", LaravelMessages.UNIQUE);
		}
		if (request.fields() != null) {
			List<String> codes = request.fields().stream().map(CreateSupplierFieldRequest::code).toList();
			errors.distinct("fields", "code", codes);
			errors.unique("fields", "code", codes, fields.findExistingCodes(codes));
		}
		errors.throwIfAny();
	}

	private static void throwCodeTaken(String field) {
		ValidationErrors errors = new ValidationErrors();
		errors.add(field, LaravelMessages.UNIQUE);
		errors.throwIfAny();
	}

}
