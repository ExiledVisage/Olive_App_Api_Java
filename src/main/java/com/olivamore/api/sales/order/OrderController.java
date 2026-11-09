package com.olivamore.api.sales.order;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
import java.util.Optional;

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
	@RequiresPermission("order.view")
	ListResponse<OrderResponse> index(@RequestParam(required = false) String status,
		@RequestParam(name = "customer_id", required = false) Long customerId, HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(responses.list(status, customerId));
		}
		int page = Listing.currentPage(request);
		Page<OrderResponse> rows = responses.page(status, customerId, page, perPage.get());
		return LaravelPaginator.paginate(rows.getContent(), rows.getTotalElements(), page, perPage.get(), request);
	}

	@GetMapping("/{id}")
	@RequiresPermission("order.view")
	DataResponse<OrderResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	@GetMapping("/{id}/stock-check")
	@RequiresPermission("order.view")
	DataResponse<StockCheckResponse> stockCheck(@PathVariable long id) {
		return DataResponse.of(service.stockCheck(id));
	}

	@PostMapping
	@RequiresPermission("order.create")
	ResponseEntity<DataResponse<OrderResponse>> store(@Valid @RequestBody CreateOrderRequest request, CurrentUser user) {
		return DataResponse.created(responses.detail(service.create(request, user.id())));
	}

	@PatchMapping("/{id}")
	@RequiresPermission("order.update")
	DataResponse<OrderResponse> update(@PathVariable long id, @Valid @RequestBody UpdateOrderRequest request) {
		service.update(id, request);
		return DataResponse.of(responses.detail(id));
	}

	@PatchMapping("/{id}/status")
	@RequiresPermission("order.update_status")
	DataResponse<OrderResponse> updateStatus(@PathVariable long id,
		@Valid @RequestBody UpdateOrderStatusRequest request, CurrentUser user) {
		service.updateStatus(id, request, user.id());
		return DataResponse.of(responses.detail(id));
	}

	@DeleteMapping("/{id}")
	@RequiresPermission("order.update")
	Map<String, String> destroy(@PathVariable long id) {
		service.delete(id);
		return Map.of("message", "Sipariş başarıyla silindi.");
	}

}
