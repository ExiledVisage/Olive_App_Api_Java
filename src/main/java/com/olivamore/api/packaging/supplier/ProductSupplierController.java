package com.olivamore.api.packaging.supplier;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.olivamore.api.packaging.lot.PackagingLotResource;
import com.olivamore.api.shared.web.InputNormalizationFilter;

import java.util.Locale;
import java.util.Set;

/** Laravel {@code ProductSupplierController}: urun (ambalaj) tedarikcileri; zeytin tedarikcisi ayri modulde. */
@RestController
class ProductSupplierController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");

	private final LaravelValidator validator;
	private final ProductSupplierService suppliers;
	private final PackagingLotResource lotResource;

	ProductSupplierController(LaravelValidator validator, ProductSupplierService suppliers, PackagingLotResource lotResource) {
		this.validator = validator;
		this.suppliers = suppliers;
		this.lotResource = lotResource;
	}

	/** {@code ?search=} ad veya kod; {@code ?is_active=}; ada gore. */
	@GetMapping("/api/product-suppliers")
	@RequiresPermission("product_supplier.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Boolean isActive = null;
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			String value = request.getParameter("is_active");
			isActive = value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
		}
		return new ListResponse.Plain<>(suppliers.all(request.getParameter("search"), isActive).stream()
			.map(s -> ProductSupplierResource.of(s, null)).toList());
	}

	@GetMapping("/api/product-suppliers/{productSupplier}")
	@RequiresPermission("product_supplier.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "productSupplier", table = "product_suppliers", model = "ProductSupplier") Row supplier) {
		return DataResponse.of(detail(supplier.id()));
	}

	/** 201 */
	@PostMapping("/api/product-suppliers")
	@RequiresPermission("product_supplier.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductSupplierRequests.store());
		return DataResponse.created(detail(suppliers.create(v.all())));
	}

	@PatchMapping("/api/product-suppliers/{productSupplier}")
	@RequiresPermission("product_supplier.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(value = "productSupplier", table = "product_suppliers", model = "ProductSupplier") Row supplier, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductSupplierRequests.update(supplier.id()));
		suppliers.update(supplier, v.all());
		return DataResponse.of(detail(supplier.id()));
	}

	@DeleteMapping("/api/product-suppliers/{productSupplier}")
	@RequiresPermission("product_supplier.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "productSupplier", table = "product_suppliers", model = "ProductSupplier") Row supplier) {
		suppliers.delete(supplier);
		return Map.of("message", "Ürün tedarikçisi silindi.");
	}

	/** Detay: lot sayisi ve lotlar (kalemiyle). */
	private Map<String, Object> detail(long id) {
		return ProductSupplierResource.of(suppliers.byId(id), lotResource.of(suppliers.lots(id), true, false, false));
	}

}
