package com.olivamore.api.sales.order;

import com.olivamore.api.finishedproduct.ShelfOperations;
import com.olivamore.api.sales.customer.Customer;
import com.olivamore.api.sales.customer.CustomerAddress;
import com.olivamore.api.sales.customer.CustomerAddressRepository;
import com.olivamore.api.sales.customer.CustomerRepository;
import com.olivamore.api.sales.order.OrderStockQueries.ProductRow;
import com.olivamore.api.sales.order.OrderStockQueries.SlotStock;
import com.olivamore.api.sales.order.StockCheckResponse.Item;
import com.olivamore.api.sales.order.StockCheckResponse.Product;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Siparis olusturma, duzenleme ve durum gecisleri. Hazirlamada siseler raftan, sevkiyatta fabrika stogundan
 * duser, hazir siparis iptalinde raflara geri konur; hepsi durum degisikligiyle ayni transaction'da.
 */
@Service
class OrderService {

	private final SalesOrderRepository orders;
	private final OrderItemRepository items;
	private final OrderStatusHistoryRepository histories;
	private final CustomerRepository customers;
	private final CustomerAddressRepository addresses;
	private final OrderStockQueries stock;
	private final ShelfOperations shelves;

	OrderService(SalesOrderRepository orders, OrderItemRepository items, OrderStatusHistoryRepository histories,
		CustomerRepository customers, CustomerAddressRepository addresses, OrderStockQueries stock,
		ShelfOperations shelves) {
		this.orders = orders;
		this.items = items;
		this.histories = histories;
		this.customers = customers;
		this.addresses = addresses;
		this.stock = stock;
		this.shelves = shelves;
	}

	/** Taslak siparis; teslimat adresi musterinin olmali. */
	@Transactional
	long create(CreateOrderRequest request, long userId) {
		Customer customer = customer(request.customerId());
		CustomerAddress address = address(request.deliveryAddressId());
		assertItems(request.items());
		SalesOrder order = new SalesOrder(customer, address, request.orderDate().toLocalDate(), request.note(), userId);
		assertAddress(order);
		orders.save(order);
		saveItems(order.getId(), request.items());
		histories.save(new OrderStatusHistory(order.getId(), null, OrderStatus.DRAFT, userId, null));
		return order.getId();
	}

	/** Yalnizca taslak ya da onayli siparis. */
	@Transactional
	void update(long id, UpdateOrderRequest request) {
		SalesOrder order = find(id);
		if (!order.getStatus().editable()) {
			throw new BusinessRuleException("Sipariş '" + order.getStatus().value() + "' durumundayken düzenlenemez.");
		}
		if (request.customerId() != null) {
			order.changeCustomer(customer(request.customerId()));
		}
		if (request.deliveryAddressId() != null) {
			order.changeAddress(address(request.deliveryAddressId()));
		}
		if (request.orderDate() != null) {
			order.changeOrderDate(request.orderDate().toLocalDate());
		}
		order.changeNote(request.note());
		assertAddress(order);
		if (request.items() != null) {
			assertItems(request.items());
			items.deleteForOrder(id);
			saveItems(id, request.items());
		}
	}

	/** Once stok isi, sonra durum: raftan alma, fabrika stogundan dusme, raflara geri koyma. */
	@Transactional
	void updateStatus(long id, UpdateOrderStatusRequest request, long userId) {
		OrderStatus to = request.status();
		if (to == OrderStatus.DRAFT) {
			throw ValidationFailedException.of("status", MessageKeys.IN);
		}
		assertPickReferences(request.picks());
		SalesOrder order = find(id);
		OrderStatus from = order.getStatus();
		if (!from.transitions().contains(to)) {
			throw new BusinessRuleException("'" + from.value() + "' durumundan '" + to.value() + "' durumuna geçiş yapılamaz.");
		}
		if (to == OrderStatus.READY) {
			pickFromShelves(id, request.picks(), userId);
		}
		if (to == OrderStatus.SHIPPED) {
			releaseTotalStock(id);
		}
		if (to == OrderStatus.CANCELLED && from == OrderStatus.READY) {
			shelves.returnOrderPicks(id, userId);
		}
		order.changeStatus(to);
		histories.save(new OrderStatusHistory(id, from, to, userId, request.note()));
	}

	@Transactional
	void delete(long id) {
		SalesOrder order = find(id);
		if (order.getStatus() != OrderStatus.DRAFT) {
			throw new BusinessRuleException("Sadece taslak (draft) durumdaki siparişler silinebilir.");
		}
		orders.delete(order);
	}

	/** Her kalem icin fabrikada yeterli sise var mi ve hangi depo/raftan alinir. Hicbir seyi kilitlemez. */
	@Transactional(readOnly = true)
	StockCheckResponse stockCheck(long id) {
		SalesOrder order = find(id);
		List<OrderItem> orderItems = items.findByOrderIdOrderByIdAsc(id);
		List<Long> productIds = orderItems.stream().map(OrderItem::getFinishedProductId).toList();
		Map<Long, ProductRow> products = stock.products(productIds);
		Map<Long, Long> inStorages = stock.inStorages(productIds);
		Map<Long, Long> committed = stock.committed(productIds, id);
		Map<Long, Long> awaiting = stock.awaitingShipment(productIds);
		var locations = stock.locations(productIds);
		List<Item> out = new ArrayList<>();
		boolean fulfillable = true;
		for (OrderItem item : orderItems) {
			ProductRow product = products.get(item.getFinishedProductId());
			long requested = item.getQuantity();
			long stored = inStorages.getOrDefault(product.id(), 0L);
			long reserved = committed.getOrDefault(product.id(), 0L);
			long available = Math.max(0, product.totalStock() - reserved);
			long shortage = Math.max(0, requested - available);
			fulfillable &= shortage == 0;
			out.add(new Item(item.getId(),
				new Product(product.id(), product.name(), product.volumeLitre().doubleValue()), requested,
				product.totalStock(), stored,
				Math.max(0, product.totalStock() - stored - awaiting.getOrDefault(product.id(), 0L)), reserved, available,
				shortage, shortage == 0, locations.getOrDefault(product.id(), List.of())));
		}
		return new StockCheckResponse(id, order.getStatus(), fulfillable, out);
	}

	private void pickFromShelves(long orderId, List<UpdateOrderStatusRequest.Pick> picks, long actorId) {
		List<OrderItem> orderItems = items.findByOrderIdOrderByIdAsc(orderId);
		if (orderItems.isEmpty()) {
			throw new BusinessRuleException("Kalemi olmayan sipariş hazırlanamaz.");
		}
		Map<Long, ProductRow> products = stock.products(
			orderItems.stream().map(OrderItem::getFinishedProductId).toList());
		List<ShelfOperations.Pick> resolved = picks == null || picks.isEmpty()
			? autoPicks(orderItems, products)
			: chosenPicks(orderItems, products, picks);
		shelves.pickForOrder(orderId, resolved, actorId);
	}

	/** Raf secimi yoksa: her kalem icin urunun rafa en eski girisinden baslayarak (FIFO). */
	private List<ShelfOperations.Pick> autoPicks(List<OrderItem> orderItems, Map<Long, ProductRow> products) {
		List<ShelfOperations.Pick> resolved = new ArrayList<>();
		for (OrderItem item : orderItems) {
			long productId = item.getFinishedProductId();
			long remaining = item.getQuantity();
			for (SlotStock slot : stock.shelvesFifo(productId)) {
				if (remaining <= 0) {
					break;
				}
				long take = Math.min(remaining, slot.quantity());
				resolved.add(new ShelfOperations.Pick(slot.slotId(), productId, take));
				remaining -= take;
			}
			if (remaining > 0) {
				throw new BusinessRuleException(productName(products, productId) + " için raflarda yeterli adet yok: "
					+ remaining + " adet eksik. Önce dolum yapılıp ürün rafa konulmalı.");
			}
		}
		return resolved;
	}

	/** Iscinin sectigi raflar: her secim bu siparisin bir kalemine ait, kalem basina toplam istenen adede esit. */
	private List<ShelfOperations.Pick> chosenPicks(List<OrderItem> orderItems, Map<Long, ProductRow> products,
		List<UpdateOrderStatusRequest.Pick> picks) {
		Map<Long, OrderItem> byId = orderItems.stream()
			.collect(Collectors.toMap(OrderItem::getId, Function.identity()));
		Map<Long, Long> perItem = new LinkedHashMap<>();
		List<ShelfOperations.Pick> resolved = new ArrayList<>();
		for (UpdateOrderStatusRequest.Pick pick : picks) {
			OrderItem item = byId.get(pick.orderItemId());
			if (item == null) {
				throw new BusinessRuleException("#" + pick.orderItemId() + " numaralı kalem bu siparişe ait değil.");
			}
			perItem.merge(item.getId(), pick.quantity().longValue(), Long::sum);
			resolved.add(new ShelfOperations.Pick(pick.slotId(), item.getFinishedProductId(), pick.quantity()));
		}
		for (OrderItem item : orderItems) {
			long picked = perItem.getOrDefault(item.getId(), 0L);
			if (picked != item.getQuantity()) {
				throw new BusinessRuleException(productName(products, item.getFinishedProductId()) + " için "
					+ item.getQuantity() + " adet gerekiyor, " + picked + " adet seçildi.");
			}
		}
		return resolved;
	}

	private void releaseTotalStock(long orderId) {
		List<OrderItem> orderItems = items.findByOrderIdOrderByIdAsc(orderId);
		Map<Long, ProductRow> products = stock.products(
			orderItems.stream().map(OrderItem::getFinishedProductId).toList());
		for (OrderItem item : orderItems) {
			stock.releaseTotalStock(products.get(item.getFinishedProductId()), item.getQuantity());
		}
	}

	private void saveItems(long orderId, List<OrderItemRequest> requests) {
		requests.forEach(request -> items.save(new OrderItem(orderId, request)));
	}

	private void assertItems(List<OrderItemRequest> requests) {
		List<Long> productIds = requests.stream().map(OrderItemRequest::finishedProductId).toList();
		ValidationErrors errors = new ValidationErrors();
		errors.exists("items", "finished_product_id", productIds, stock.products(productIds).keySet());
		errors.distinct("items", "finished_product_id", productIds);
		errors.throwIfAny();
	}

	private void assertPickReferences(List<UpdateOrderStatusRequest.Pick> picks) {
		if (picks == null || picks.isEmpty()) {
			return;
		}
		List<Long> itemIds = picks.stream().map(UpdateOrderStatusRequest.Pick::orderItemId).toList();
		List<Long> slotIds = picks.stream().map(UpdateOrderStatusRequest.Pick::slotId).toList();
		ValidationErrors errors = new ValidationErrors();
		errors.exists("picks", "order_item_id", itemIds, items.findAllById(itemIds).stream().map(OrderItem::getId).toList());
		errors.exists("picks", "slot_id", slotIds, stock.existingSlots(slotIds));
		errors.throwIfAny();
	}

	private static void assertAddress(SalesOrder order) {
		if (!order.addressBelongsToCustomer()) {
			throw new BusinessRuleException("Teslimat adresi bu müşteriye ait değil.");
		}
	}

	private static String productName(Map<Long, ProductRow> products, long id) {
		ProductRow product = products.get(id);
		return product != null && product.name() != null ? product.name() : "#" + id;
	}

	private SalesOrder find(long id) {
		return orders.findById(id).orElseThrow(() -> new ModelNotFoundException("Order", id));
	}

	private Customer customer(long id) {
		return customers.findById(id).orElseThrow(() -> invalid("customer_id"));
	}

	private CustomerAddress address(long id) {
		return addresses.findById(id).orElseThrow(() -> invalid("delivery_address_id"));
	}

	private static ValidationFailedException invalid(String field) {
		return ValidationFailedException.of(field, MessageKeys.EXISTS);
	}

}
