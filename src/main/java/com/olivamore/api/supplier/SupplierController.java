package com.olivamore.api.supplier;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.InputNormalizationFilter;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code SupplierController}: zeytin tedarikcileri, tarlalari ve teslimatlari.
 * (Ambalaj tedarikcileri, {@code product-suppliers}, packaging modulunde.)
 */
@RestController
class SupplierController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");

	private final LaravelValidator validator;
	private final SupplierService suppliers;
	private final SupplierDeliveryService deliveries;
	private final SupplierResource resource;

	SupplierController(LaravelValidator validator, SupplierService suppliers, SupplierDeliveryService deliveries,
		SupplierResource resource) {
		this.validator = validator;
		this.suppliers = suppliers;
		this.deliveries = deliveries;
		this.resource = resource;
	}

	// ---- okuma (supplier.view) ---------------------------------------------

	/**
	 * {@code ?is_active}: istekte varsa (bos olsa bile) {@code filter_var(BOOLEAN)} ile filtre;
	 * {@code ?search}: ad, kod ya da tarla kodunda buyuk/kucuk harfe duyarsiz.
	 */
	@GetMapping("/api/suppliers")
	@RequiresPermission("supplier.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Boolean isActive = InputNormalizationFilter.wasSent(request, "is_active")
			? filterBoolean(request.getParameter("is_active")) : null;
		String search = request.getParameter("search") == null ? "" : request.getParameter("search").trim();
		return new ListResponse.Plain<>(resource.suppliers(suppliers.all(isActive, search)));
	}

	@GetMapping("/api/suppliers/{supplier}")
	@RequiresPermission("supplier.view")
	DataResponse<Map<String, Object>> show(@Bound(table = "suppliers", model = "Supplier") Row supplier) {
		return DataResponse.of(resource.detail(supplier));
	}

	/** Tedarikcinin partileri (en yeni once) ve her partinin kantar tartimi. */
	@GetMapping("/api/suppliers/{supplier}/deliveries")
	@RequiresPermission("supplier.view")
	Map<String, Object> deliveries(@Bound(table = "suppliers", model = "Supplier") Row supplier) {
		List<Map<String, Object>> list = deliveries.forSupplier(supplier.id());
		BigDecimal total = BigDecimal.ZERO;
		for (Map<String, Object> d : list) {
			total = total.add(floatval(d.get("weighed_quantity_kg")));
		}
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("delivery_count", list.size());
		summary.put("total_weighed_kg", phpFloat(total.setScale(2, RoundingMode.HALF_UP)));
		summary.put("last_received_at", list.isEmpty() ? null : list.getFirst().get("received_at"));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("data", list);
		body.put("summary", summary);
		return body;
	}

	// ---- yazma (supplier.manage) -------------------------------------------

	/** Cevap 201: olusan model (yalnizca verilen alanlar) + tarlalar ve parti sayisi. */
	@PostMapping("/api/suppliers")
	@RequiresPermission("supplier.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, SupplierRequests.store());
		return DataResponse.created(resource.detail(suppliers.create(v.all())));
	}

	@PatchMapping("/api/suppliers/{supplier}")
	@RequiresPermission("supplier.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(table = "suppliers", model = "Supplier") Row supplier, RequestInput input) {
		ValidatedInput v = validator.validate(input, SupplierRequests.update(supplier.id()));
		return DataResponse.of(resource.detail(suppliers.update(supplier, v.all())));
	}

	@DeleteMapping("/api/suppliers/{supplier}")
	@RequiresPermission("supplier.manage")
	@Transactional
	Map<String, String> destroy(@Bound(table = "suppliers", model = "Supplier") Row supplier) {
		suppliers.delete(supplier);
		return Map.of("message", "Tedarikçi silindi.");
	}

	@PostMapping("/api/suppliers/{supplier}/fields")
	@RequiresPermission("supplier.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeField(
		@Bound(table = "suppliers", model = "Supplier") Row supplier, RequestInput input) {
		ValidatedInput v = validator.validate(input, SupplierRequests.storeField());
		return DataResponse.created(SupplierResource.field(suppliers.createField(supplier, v.all())));
	}

	/** Once validation (FormRequest), sonra "bu tedarikcinin mi" (abort 404). */
	@PatchMapping("/api/suppliers/{supplier}/fields/{field}")
	@RequiresPermission("supplier.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateField(@Bound(table = "suppliers", model = "Supplier") Row supplier,
		@Bound(table = "supplier_fields", model = "SupplierField") Row field, RequestInput input) {
		ValidatedInput v = validator.validate(input, SupplierRequests.updateField(field.id()));
		SupplierService.ensureFieldOf(supplier, field);
		return DataResponse.of(SupplierResource.field(suppliers.updateField(field, v.all())));
	}

	@DeleteMapping("/api/suppliers/{supplier}/fields/{field}")
	@RequiresPermission("supplier.manage")
	@Transactional
	Map<String, String> destroyField(@Bound(table = "suppliers", model = "Supplier") Row supplier,
		@Bound(table = "supplier_fields", model = "SupplierField") Row field) {
		SupplierService.ensureFieldOf(supplier, field);
		suppliers.deleteField(field);
		return Map.of("message", "Tarla silindi.");
	}

	// ---- yardimcilar -------------------------------------------------------

	/** {@code filter_var($v, FILTER_VALIDATE_BOOLEAN)}: "1", "true", "on", "yes" -> true, gerisi false. */
	private static boolean filterBoolean(String value) {
		return value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
	}

	/** PHP (float) */
	private static BigDecimal floatval(Object value) {
		if (value == null || value instanceof Boolean b && !b) {
			return BigDecimal.ZERO;
		}
		if (value instanceof Boolean) {
			return BigDecimal.ONE;
		}
		if (value instanceof Number n) {
			return new BigDecimal(n.toString());
		}
		try {
			return new BigDecimal(Php.strval(value).strip());
		}
		catch (NumberFormatException e) {
			return BigDecimal.valueOf(Php.intval(value));
		}
	}

	/** PHP json_encode float: tam sayiya esitse ".0" yazilmaz (64765.0 -> 64765). */
	private static Object phpFloat(BigDecimal value) {
		BigDecimal stripped = value.stripTrailingZeros();
		return stripped.scale() <= 0 ? (Object) stripped.longValue() : stripped.doubleValue();
	}

}
