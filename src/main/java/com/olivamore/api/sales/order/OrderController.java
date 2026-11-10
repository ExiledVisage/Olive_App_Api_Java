package com.olivamore.api.sales.order;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.Pages;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/** Siparisler, kalemler, durum gecisleri, stok kontrolu. */
@RestController
@RequestMapping("/api/orders")
class OrderController {

	private final OrderService service;
	private final OrderResponses responses;

	OrderController(OrderService service, OrderResponses responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('order.view')")
	PagedModel<OrderResponse> index(@RequestParam(required = false) String status,
		@RequestParam(name = "customer_id", required = false) Long customerId, Pageable pageable) {
		return Pages.of(pageable, requested -> responses.page(status, customerId, requested),
			() -> responses.list(status, customerId));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('order.view')")
	DataResponse<OrderResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	@GetMapping("/{id}/stock-check")
	@PreAuthorize("hasAuthority('order.view')")
	DataResponse<StockCheckResponse> stockCheck(@PathVariable long id) {
		return DataResponse.of(service.stockCheck(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('order.create')")
	ResponseEntity<DataResponse<OrderResponse>> store(@Valid @RequestBody CreateOrderRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(responses.detail(service.create(request, user.id())));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('order.update')")
	DataResponse<OrderResponse> update(@PathVariable long id, @Valid @RequestBody UpdateOrderRequest request) {
		service.update(id, request);
		return DataResponse.of(responses.detail(id));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasAuthority('order.update_status')")
	DataResponse<OrderResponse> updateStatus(@PathVariable long id,
		@Valid @RequestBody UpdateOrderStatusRequest request, @AuthenticationPrincipal CurrentUser user) {
		service.updateStatus(id, request, user.id());
		return DataResponse.of(responses.detail(id));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('order.update')")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Sipariş başarıyla silindi.");
	}

}
