package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Bitmis urunler. */
@RestController
class FinishedProductController {

	private final FinishedProductService products;
	private final FinishedProductResponses responses;

	FinishedProductController(FinishedProductService products, FinishedProductResponses responses) {
		this.products = products;
		this.responses = responses;
	}

	/** En yeni once; tarif, bilesenler, raf konumlari ve depo toplamlari yuklu. */
	@GetMapping("/api/finished-products")
	@RequiresPermission("finished_product.view")
	ListResponse<FinishedProductResponse> index(
		@RequestParam(name = "is_active", required = false) Boolean isActive) {
		return new ListResponse.Plain<>(responses.list(isActive));
	}

	/** Detay: tarifin icerigi de. */
	@GetMapping("/api/finished-products/{id}")
	@RequiresPermission("finished_product.view")
	DataResponse<FinishedProductResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	/** 201; cevapta depo toplami var, sevkiyat bekleyen ve konumlar yok. */
	@PostMapping("/api/finished-products")
	@RequiresPermission("finished_product.manage")
	ResponseEntity<DataResponse<FinishedProductResponse>> store(
		@Valid @RequestBody CreateFinishedProductRequest request) {
		return DataResponse.created(responses.written(products.create(request)));
	}

	@RequestMapping(path = "/api/finished-products/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("finished_product.manage")
	DataResponse<FinishedProductResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateFinishedProductRequest request) {
		products.update(id, request);
		return DataResponse.of(responses.written(id));
	}

	@DeleteMapping("/api/finished-products/{id}")
	@RequiresPermission("finished_product.manage")
	Map<String, String> destroy(@PathVariable long id) {
		products.delete(id);
		return Map.of("message", "Son ürün başarıyla silindi.");
	}

}
