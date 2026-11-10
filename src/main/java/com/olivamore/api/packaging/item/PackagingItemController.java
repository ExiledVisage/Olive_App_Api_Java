package com.olivamore.api.packaging.item;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@RestController
@RequestMapping("/api/packaging-items")
class PackagingItemController {

	private final PackagingItemService service;

	PackagingItemController(PackagingItemService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('packaging_item.view')")
	ListResponse<PackagingItemResponse> index(@RequestParam(name = "is_active", required = false) Boolean isActive) {
		return new ListResponse.Plain<>(service.all(isActive));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('packaging_item.view')")
	DataResponse<PackagingItemResponse> show(@PathVariable long id) {
		return DataResponse.of(service.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('packaging_item.manage')")
	ResponseEntity<DataResponse<PackagingItemResponse>> store(@Valid @RequestBody CreatePackagingItemRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(service.get(service.create(request, user.id())));
	}

	@RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@PreAuthorize("hasAuthority('packaging_item.manage')")
	DataResponse<PackagingItemResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdatePackagingItemRequest request) {
		service.update(id, request);
		return DataResponse.of(service.get(id));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('packaging_item.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Ambalaj kalemi başarıyla silindi.");
	}

}
