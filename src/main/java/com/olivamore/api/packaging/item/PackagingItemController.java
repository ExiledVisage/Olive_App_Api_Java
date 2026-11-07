package com.olivamore.api.packaging.item;

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
import com.olivamore.api.shared.web.InputNormalizationFilter;

import java.util.Locale;
import java.util.Set;

/** Laravel {@code PackagingItemController}: ambalaj kalemleri (sise, kapak, etiket). */
@RestController
class PackagingItemController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");

	private final LaravelValidator validator;
	private final PackagingItemService items;

	PackagingItemController(LaravelValidator validator, PackagingItemService items) {
		this.validator = validator;
		this.items = items;
	}

	/** {@code ?is_active} (istekte varsa filter_var BOOLEAN); en yeni once. */
	@GetMapping("/api/packaging-items")
	@RequiresPermission("packaging_item.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Boolean isActive = null;
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			String value = request.getParameter("is_active");
			isActive = value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
		}
		return new ListResponse.Plain<>(items.all(isActive).stream().map(PackagingItemResource::of).toList());
	}

	@GetMapping("/api/packaging-items/{packagingItem}")
	@RequiresPermission("packaging_item.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item) {
		return DataResponse.of(PackagingItemResource.of(items.byId(item.id())));
	}

	/** Yeni kalem (201). */
	@PostMapping("/api/packaging-items")
	@RequiresPermission("packaging_item.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, PackagingItemRequests.store());
		return DataResponse.created(PackagingItemResource.of(items.byId(items.create(v.all(), user.id()))));
	}

	@RequestMapping(path = "/api/packaging-items/{packagingItem}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("packaging_item.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, PackagingItemRequests.update());
		items.update(item, v.all());
		return DataResponse.of(PackagingItemResource.of(items.byId(item.id())));
	}

	@DeleteMapping("/api/packaging-items/{packagingItem}")
	@RequiresPermission("packaging_item.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item) {
		items.delete(item);
		return Map.of("message", "Ambalaj kalemi başarıyla silindi.");
	}

}
