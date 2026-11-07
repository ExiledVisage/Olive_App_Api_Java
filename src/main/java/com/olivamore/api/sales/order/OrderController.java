package com.olivamore.api.sales.order;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Laravel {@code OrderController}: siparisler, kalemler, durum gecisleri, stok kontrolu. */
@RestController
class OrderController {

	private final Db db;
	private final LaravelValidator validator;
	private final OrderService orders;
	private final OrderResource resource;

	OrderController(Db db, LaravelValidator validator, OrderService orders, OrderResource resource) {
		this.db = db;
		this.validator = validator;
		this.orders = orders;
		this.resource = resource;
	}

	// ---- okuma (order.view) ------------------------------------------------------------

	/** {@code ?status}, {@code ?customer_id}; siparis gunu sonra olusma zamani, yeniden eskiye; {@code per_page}. */
	@GetMapping("/api/orders")
	@RequiresPermission("order.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		List<String> where = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		for (String key : List.of("status", "customer_id")) {
			String value = request.getParameter(key);
			if (value != null) {
				where.add(key + " = ?");
				args.add(value);
			}
		}
		String filter = where.isEmpty() ? "" : " where " + String.join(" and ", where);
		String order = " order by order_date desc, created_at desc";
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(resource.orders(db.select("select * from orders" + filter + order, args.toArray()), false));
		}
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate from orders" + filter, args.toArray());
		List<Row> rows = total == 0 ? List.of() : db.select("select * from orders" + filter + order
			+ " limit " + perPage.get() + " offset " + (long) (page - 1) * perPage.get(), args.toArray());
		return LaravelPaginator.paginate(resource.orders(rows, false), total, page, perPage.get(), request);
	}

	@GetMapping("/api/orders/{order}")
	@RequiresPermission("order.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "order", table = "orders", model = "Order") Row order) {
		return DataResponse.of(resource.detail(order.id()));
	}

	@GetMapping("/api/orders/{order}/stock-check")
	@RequiresPermission("order.view")
	Map<String, Object> stockCheck(@Bound(value = "order", table = "orders", model = "Order") Row order) {
		return Map.of("data", orders.stockCheck(order));
	}

	// ---- yazma -------------------------------------------------------------------------

	/** Taslak siparis (201). */
	@PostMapping("/api/orders")
	@RequiresPermission("order.create")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, OrderRequests.store());
		return DataResponse.created(resource.detail(orders.create(v.all(), user.id())));
	}

	@PatchMapping("/api/orders/{order}")
	@RequiresPermission("order.update")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "order", table = "orders", model = "Order") Row order, RequestInput input) {
		ValidatedInput v = validator.validate(input, OrderRequests.update());
		orders.update(order, v.all());
		return DataResponse.of(resource.detail(order.id()));
	}

	@PatchMapping("/api/orders/{order}/status")
	@RequiresPermission("order.update_status")
	@Transactional
	DataResponse<Map<String, Object>> updateStatus(@Bound(value = "order", table = "orders", model = "Order") Row order,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, OrderRequests.updateStatus());
		orders.updateStatus(order, Php.strval(v.get("status")), v.get("note"), v.get("picks"), user.id());
		return DataResponse.of(resource.detail(order.id()));
	}

	@DeleteMapping("/api/orders/{order}")
	@RequiresPermission("order.update")
	@Transactional
	Map<String, String> destroy(@Bound(value = "order", table = "orders", model = "Order") Row order) {
		orders.delete(order);
		return Map.of("message", "Sipariş başarıyla silindi.");
	}

}
