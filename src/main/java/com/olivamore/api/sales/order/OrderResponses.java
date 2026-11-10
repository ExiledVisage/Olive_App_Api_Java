package com.olivamore.api.sales.order;

import com.olivamore.api.finishedproduct.FinishedProductDirectory;
import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.sales.customer.CustomerAddressResponse;
import com.olivamore.api.sales.customer.CustomerResponse;
import com.olivamore.api.sales.order.OrderResponse.History;
import com.olivamore.api.sales.order.OrderResponse.Item;
import com.olivamore.api.sales.order.OrderResponse.Product;
import com.olivamore.api.sales.order.OrderResponse.UserSummary;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entity'den API cevabina. Urun ve kullanici bilgileri salt okunur. */
@Component
class OrderResponses {

	private static final Sort NEWEST_FIRST =
		Sort.by(Sort.Order.desc("orderDate"), Sort.Order.desc("createdAt"));

	private final SalesOrderRepository orders;
	private final OrderItemRepository items;
	private final OrderStatusHistoryRepository histories;
	private final FinishedProductDirectory finishedProducts;
	private final UserDirectory users;

	OrderResponses(SalesOrderRepository orders, OrderItemRepository items, OrderStatusHistoryRepository histories,
		FinishedProductDirectory finishedProducts, UserDirectory users) {
		this.orders = orders;
		this.items = items;
		this.histories = histories;
		this.finishedProducts = finishedProducts;
		this.users = users;
	}

	@Transactional(readOnly = true)
	List<OrderResponse> list(String status, Long customerId) {
		return responses(orders.findAll(matching(status, customerId), NEWEST_FIRST));
	}

	@Transactional(readOnly = true)
	Page<OrderResponse> page(String status, Long customerId, int page, int size) {
		Page<SalesOrder> rows = orders.findAll(matching(status, customerId), PageRequest.of(page - 1, size, NEWEST_FIRST));
		Map<Long, OrderResponse> responses = responses(rows.getContent()).stream()
			.collect(Collectors.toMap(OrderResponse::id, Function.identity()));
		return rows.map(order -> responses.get(order.getId()));
	}

	/** Olusturan kullanici ve durum gecmisiyle. */
	@Transactional(readOnly = true)
	OrderResponse detail(long id) {
		SalesOrder order = orders.findById(id).orElseThrow(() -> new ModelNotFoundException("Order", id));
		List<OrderStatusHistory> rows = histories.findByOrderIdOrderByChangedAtAscIdAsc(id);
		List<Long> userIds = new ArrayList<>(rows.stream().map(OrderStatusHistory::getChangedByUserId).toList());
		userIds.add(order.getCreatedByUserId());
		Map<Long, UserSummary> users = users(userIds);
		List<History> history = rows.stream()
			.map(row -> new History(row.getFromStatus(), row.getToStatus(), user(users, row.getChangedByUserId()),
				row.getNote(), row.getChangedAt()))
			.toList();
		return response(order, itemsOf(List.of(order)).getOrDefault(id, List.of()),
			user(users, order.getCreatedByUserId()), history);
	}

	private List<OrderResponse> responses(List<SalesOrder> rows) {
		Map<Long, List<Item>> byOrder = itemsOf(rows);
		return rows.stream()
			.map(order -> response(order, byOrder.getOrDefault(order.getId(), List.of()), null, null))
			.toList();
	}

	private static OrderResponse response(SalesOrder order, List<Item> orderItems, UserSummary creator,
		List<History> history) {
		return new OrderResponse(order.getId(), order.getStatus(), order.getStatus().transitions(),
			order.getOrderDate().atStartOfDay(), order.getNote(),
			CustomerResponse.withoutAddresses(order.getCustomer()),
			CustomerAddressResponse.of(order.getDeliveryAddress()), orderItems, creator, history,
			order.getCreatedAt(), order.getUpdatedAt());
	}

	private Map<Long, List<Item>> itemsOf(List<SalesOrder> rows) {
		List<OrderItem> orderItems = items.findByOrderIdInOrderByIdAsc(rows.stream().map(SalesOrder::getId).toList());
		Map<Long, Product> products = products(orderItems.stream().map(OrderItem::getFinishedProductId).toList());
		return orderItems.stream().collect(Collectors.groupingBy(OrderItem::getOrderId,
			Collectors.mapping(item -> new Item(item.getId(), products.get(item.getFinishedProductId()),
				item.getQuantity()), Collectors.toList())));
	}

	private Map<Long, Product> products(List<Long> ids) {
		return finishedProducts.products(ids).values().stream().collect(Collectors.toMap(
			FinishedProductDirectory.Product::id,
			p -> new Product(p.id(), p.name(), p.volumeLitre(), (int) p.totalStock(), null, null, null, p.isActive(),
				p.createdAt(), p.updatedAt())));
	}

	private Map<Long, UserSummary> users(List<Long> ids) {
		return users.users(ids).values().stream().collect(Collectors.toMap(UserDirectory.User::id,
			u -> new UserSummary(u.id(), u.name())));
	}

	private static UserSummary user(Map<Long, UserSummary> users, Long id) {
		return id == null ? null : users.get(id);
	}

	/** Bos filtre uygulanmaz; bilinmeyen durum sonuc dondurmez. */
	private static Specification<SalesOrder> matching(String status, Long customerId) {
		return (root, query, cb) -> {
			var predicate = cb.conjunction();
			if (customerId != null) {
				predicate = cb.and(predicate, cb.equal(root.get("customer").get("id"), customerId));
			}
			if (status != null && !status.isBlank()) {
				var filter = Arrays.stream(OrderStatus.values())
					.filter(candidate -> candidate.value().equals(status))
					.findFirst()
					.map(candidate -> cb.equal(root.get("status"), candidate))
					.orElseGet(cb::disjunction);
				predicate = cb.and(predicate, filter);
			}
			return predicate;
		};
	}

}
