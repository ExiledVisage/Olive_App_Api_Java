package com.olivamore.api.finishedproduct.product;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.olivamore.api.shared.web.InputNormalizationFilter;

import java.util.Locale;
import java.util.Set;

/** Laravel {@code FinishedProductController}: bitmis urunler. */
@RestController
class FinishedProductController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");

	private final LaravelValidator validator;
	private final FinishedProductService products;
	private final FinishedProductResource resource;

	FinishedProductController(LaravelValidator validator, FinishedProductService products, FinishedProductResource resource) {
		this.validator = validator;
		this.products = products;
		this.resource = resource;
	}

	/** {@code ?is_active}; en yeni once; tarif, bilesenler, raf konumlari ve depo toplamlari yuklu. */
	@GetMapping("/api/finished-products")
	@RequiresPermission("finished_product.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Boolean isActive = null;
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			String value = request.getParameter("is_active");
			isActive = value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
		}
		return new ListResponse.Plain<>(resource.of(products.all(isActive), false, true, true));
	}

	/** Detay: tarifin icerigi de. */
	@GetMapping("/api/finished-products/{product}")
	@RequiresPermission("finished_product.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "product", table = "finished_products", model = "FinishedProduct") Row product) {
		return DataResponse.of(resource.of(List.of(products.byId(product.id())), true, true, true).getFirst());
	}

	/** 201; cevapta depo toplami var, sevkiyat bekleyen ve konumlar yok (Laravel'de yuklenmiyor). */
	@PostMapping("/api/finished-products")
	@RequiresPermission("finished_product.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductRequests.store());
		return DataResponse.created(written(products.create(v)));
	}

	@RequestMapping(path = "/api/finished-products/{product}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("finished_product.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "product", table = "finished_products", model = "FinishedProduct") Row product,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductRequests.update());
		products.update(product, v);
		return DataResponse.of(written(product.id()));
	}

	@DeleteMapping("/api/finished-products/{product}")
	@RequiresPermission("finished_product.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "product", table = "finished_products", model = "FinishedProduct") Row product) {
		products.delete(product);
		return Map.of("message", "Son ürün başarıyla silindi.");
	}

	/** Ekleme/guncelleme cevabi: tarif (icerik yok), bilesenler, depo toplami. */
	private Map<String, Object> written(long id) {
		return resource.of(List.of(products.written(id)), false, false, false).getFirst();
	}

}
