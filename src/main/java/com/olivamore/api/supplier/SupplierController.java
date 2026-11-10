package com.olivamore.api.supplier;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Zeytin tedarikcileri, tarlalari ve teslimatlari. */
@RestController
@RequestMapping("/api/suppliers")
class SupplierController {

	private final SupplierService suppliers;
	private final SupplierDeliveryService deliveries;

	SupplierController(SupplierService suppliers, SupplierDeliveryService deliveries) {
		this.suppliers = suppliers;
		this.deliveries = deliveries;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('supplier.view')")
	ListResponse<SupplierResponse> index(@RequestParam(name = "is_active", required = false) Boolean isActive,
		@RequestParam(required = false) String search) {
		return new ListResponse<>(suppliers.all(isActive, search));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('supplier.view')")
	DataResponse<SupplierResponse> show(@PathVariable long id) {
		return DataResponse.of(suppliers.get(id));
	}

	@GetMapping("/{id}/deliveries")
	@PreAuthorize("hasAuthority('supplier.view')")
	SupplierDeliveriesResponse deliveries(@PathVariable long id) {
		return deliveries.forSupplier(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('supplier.manage')")
	ResponseEntity<DataResponse<SupplierResponse>> store(@Valid @RequestBody CreateSupplierRequest request) {
		return DataResponse.created(suppliers.create(request));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('supplier.manage')")
	DataResponse<SupplierResponse> update(@PathVariable long id, @Valid @RequestBody UpdateSupplierRequest request) {
		return DataResponse.of(suppliers.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('supplier.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		suppliers.delete(id);
		return Map.of("message", "Tedarikçi silindi.");
	}

	@PostMapping("/{id}/fields")
	@PreAuthorize("hasAuthority('supplier.manage')")
	ResponseEntity<DataResponse<SupplierFieldResponse>> storeField(@PathVariable long id,
		@Valid @RequestBody CreateSupplierFieldRequest request) {
		return DataResponse.created(suppliers.createField(id, request));
	}

	@PatchMapping("/{id}/fields/{fieldId}")
	@PreAuthorize("hasAuthority('supplier.manage')")
	DataResponse<SupplierFieldResponse> updateField(@PathVariable long id, @PathVariable long fieldId,
		@Valid @RequestBody UpdateSupplierFieldRequest request) {
		return DataResponse.of(suppliers.updateField(id, fieldId, request));
	}

	@DeleteMapping("/{id}/fields/{fieldId}")
	@PreAuthorize("hasAuthority('supplier.manage')")
	Map<String, String> destroyField(@PathVariable long id, @PathVariable long fieldId) {
		suppliers.deleteField(id, fieldId);
		return Map.of("message", "Tarla silindi.");
	}

}
