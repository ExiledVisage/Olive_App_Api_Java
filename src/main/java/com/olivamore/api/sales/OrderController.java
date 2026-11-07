package com.olivamore.api.sales;

import com.olivamore.api.finishedproduct.ShelfOperations;
import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Laravel {@code OrderController} + {@code OrderService}: siparisler, kalemler, durum gecisleri. Hazirlamada
 * (ready) siseler raftan duser, sevkiyatta (shipped) fabrika stogundan, hazir siparis iptalinde raflara geri
 * konur; hepsi durum degisikligiyle ayni transaction'da.
 */
@RestController
class OrderController {

	private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Db db;
	private final LaravelValidator validator;
	private final OrderReads reads;
	private final ShelfOperations shelves;

	OrderController(Db db, LaravelValidator validator, OrderReads reads, ShelfOperations shelves) {
		this.db = db;
		this.validator = validator;
		this.reads = reads;
		this.shelves = shelves;
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
			return new ListResponse.Plain<>(reads.orders(db.select("select * from orders" + filter + order, args.toArray()), false));
		}
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate from orders" + filter, args.toArray());
		List<Row> rows = total == 0 ? List.of() : db.select("select * from orders" + filter + order
			+ " limit " + perPage.get() + " offset " + (long) (page - 1) * perPage.get(), args.toArray());
		return LaravelPaginator.paginate(reads.orders(rows, false), total, page, perPage.get(), request);
	}

	@GetMapping("/api/orders/{order}")
	@RequiresPermission("order.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "order", table = "orders", model = "Order") Row order) {
		return DataResponse.of(reads.detail(order.id()));
	}

	@GetMapping("/api/orders/{order}/stock-check")
	@RequiresPermission("order.view")
	Map<String, Object> stockCheck(@Bound(value = "order", table = "orders", model = "Order") Row order) {
		return Map.of("data", reads.stockCheck(order));
	}

	// ---- yazma -------------------------------------------------------------------------

	/** Taslak siparis (201); teslimat adresi musterinin olmali. */
	@PostMapping("/api/orders")
	@RequiresPermission("order.create")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("customer_id", "required|integer|exists:customers,id")
			.field("delivery_address_id", "required|integer|exists:customer_addresses,id")
			.field("order_date", "required|date")
			.field("note", "nullable|string")
			.field("items", "required|array|min:1")
			.field("items.*.finished_product_id", "required|integer|exists:finished_products,id|distinct")
			.field("items.*.quantity", "required|integer|min:1"));
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		Object items = data.remove("items");
		data.put("order_date", date(data.get("order_date")));
		data.put("status", "draft");
		data.put("created_by_user_id", user.id());
		long id = db.insert("orders", data);
		assertDeliveryAddressBelongsToCustomer(db.find("orders", id).orElseThrow());
		createItems(id, items);
		recordStatusChange(id, null, "draft", user.id(), null);
		return DataResponse.created(reads.detail(id));
	}

	/** Yalnizca taslak ya da onayli siparis; verilen kalemler eskilerin yerine gecer. */
	@PatchMapping("/api/orders/{order}")
	@RequiresPermission("order.update")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "order", table = "orders", model = "Order") Row order, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("customer_id", "sometimes|integer|exists:customers,id")
			.field("delivery_address_id", "sometimes|integer|exists:customer_addresses,id")
			.field("order_date", "sometimes|date")
			.field("note", "nullable|string")
			.field("items", "sometimes|array|min:1")
			.field("items.*.finished_product_id", "required_with:items|integer|exists:finished_products,id|distinct")
			.field("items.*.quantity", "required_with:items|integer|min:1"));
		String status = order.string("status");
		if (!"draft".equals(status) && !"confirmed".equals(status)) {
			throw new BusinessRuleException("Sipariş '" + status + "' durumundayken düzenlenemez.");
		}
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		Object items = data.remove("items");
		if (!data.isEmpty()) {
			if (data.containsKey("order_date")) {
				data.put("order_date", date(data.get("order_date")));
			}
			db.update("orders", order.id(), data);
			assertDeliveryAddressBelongsToCustomer(db.find("orders", order.id()).orElseThrow());
		}
		if (items != null) {
			db.execute("delete from order_items where order_id = ? and order_id is not null", order.id());
			createItems(order.id(), items);
		}
		return DataResponse.of(reads.detail(order.id()));
	}

	/**
	 * Durum gecisi. Once stok isi, sonra durum: hazirlamada raftan alma ({@code picks} yoksa FIFO), sevkiyatta
	 * fabrika stogundan dusme, hazir siparis iptalinde raflara geri koyma.
	 */
	@PatchMapping("/api/orders/{order}/status")
	@RequiresPermission("order.update_status")
	@Transactional
	DataResponse<Map<String, Object>> updateStatus(@Bound(value = "order", table = "orders", model = "Order") Row order,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("status", "required|in:confirmed,stock_check,in_production,ready,shipped,cancelled")
			.field("note", "nullable|string|max:1000")
			.field("picks", "sometimes|array")
			.field("picks.*.order_item_id", "required_with:picks|integer|exists:order_items,id")
			.field("picks.*.slot_id", "required_with:picks|integer|exists:finished_product_storage_slots,id")
			.field("picks.*.quantity", "required_with:picks|integer|min:1"));
		String from = order.string("status");
		String to = Php.strval(v.get("status"));
		if (!OrderReads.TRANSITIONS.getOrDefault(from, List.of()).contains(to)) {
			throw new BusinessRuleException("'" + from + "' durumundan '" + to + "' durumuna geçiş yapılamaz.");
		}
		if ("ready".equals(to)) {
			pickFromShelves(order.id(), v.get("picks"), user.id());
		}
		if ("shipped".equals(to)) {
			releaseTotalStock(order.id());
		}
		if ("cancelled".equals(to) && "ready".equals(from)) {
			shelves.returnOrderPicks(order.id(), user.id());
		}
		db.update("orders", order.id(), Map.of("status", to));
		recordStatusChange(order.id(), from, to, user.id(), v.get("note"));
		return DataResponse.of(reads.detail(order.id()));
	}

	@DeleteMapping("/api/orders/{order}")
	@RequiresPermission("order.update")
	@Transactional
	Map<String, String> destroy(@Bound(value = "order", table = "orders", model = "Order") Row order) {
		if (!"draft".equals(order.string("status"))) {
			throw new BusinessRuleException("Sadece taslak (draft) durumdaki siparişler silinebilir.");
		}
		db.delete("orders", order.id());
		return Map.of("message", "Sipariş başarıyla silindi.");
	}

	// ---- stok ----------------------------------------------------------------------------

	private void pickFromShelves(long orderId, Object picks, long actorId) {
		List<Row> items = db.whereIn("order_items", "order_id", List.of(orderId), null);
		if (items.isEmpty()) {
			throw new BusinessRuleException("Kalemi olmayan sipariş hazırlanamaz.");
		}
		Map<Long, Row> products = Relations.byId(db.whereIn("finished_products", "id", Relations.column(items, "finished_product_id"), null));
		List<Map<String, Object>> given = items(picks);
		List<ShelfOperations.Pick> resolved = given.isEmpty() ? autoPicks(items, products) : validatePicks(items, products, given);
		shelves.pickForOrder(orderId, resolved, actorId);
	}

	/** Raf secimi yoksa: her kalem icin urunun rafa en eski girisinden baslayarak (FIFO). */
	private List<ShelfOperations.Pick> autoPicks(List<Row> items, Map<Long, Row> products) {
		List<ShelfOperations.Pick> resolved = new ArrayList<>();
		for (Row item : items) {
			long productId = item.longValue("finished_product_id");
			long remaining = Php.intval(item.get("quantity"));
			List<Row> stocks = new ArrayList<>(db.select("select * from finished_product_storage_slot_stocks where finished_product_id = ?"
				+ " and quantity > ?", productId, 0));
			Map<Long, String> firstIn = new LinkedHashMap<>();
			for (Row stock : stocks) {
				firstIn.put(stock.id(), firstStockInAt(stock.longValue("finished_product_storage_slot_id"), productId));
			}
			stocks.sort((a, b) -> firstIn.get(a.id()).compareTo(firstIn.get(b.id())));
			for (Row stock : stocks) {
				if (remaining <= 0) {
					break;
				}
				long take = Math.min(remaining, Php.intval(stock.get("quantity")));
				resolved.add(new ShelfOperations.Pick(stock.longValue("finished_product_storage_slot_id"), productId, take));
				remaining -= take;
			}
			if (remaining > 0) {
				throw new BusinessRuleException(productName(products, productId) + " için raflarda yeterli adet yok: " + remaining
					+ " adet eksik. Önce dolum yapılıp ürün rafa konulmalı.");
			}
		}
		return resolved;
	}

	/** Iscinin sectigi raflar: her secim bu siparisin bir kalemine ait, kalem basina toplam istenen adede esit. */
	private List<ShelfOperations.Pick> validatePicks(List<Row> items, Map<Long, Row> products, List<Map<String, Object>> picks) {
		Map<Long, Row> itemsById = Relations.byId(items);
		Map<Long, Long> perItem = new LinkedHashMap<>();
		List<ShelfOperations.Pick> resolved = new ArrayList<>();
		for (Map<String, Object> pick : picks) {
			long itemId = Php.intval(pick.get("order_item_id"));
			Row item = itemsById.get(itemId);
			if (item == null) {
				throw new BusinessRuleException("#" + itemId + " numaralı kalem bu siparişe ait değil.");
			}
			long quantity = Php.intval(pick.get("quantity"));
			perItem.merge(itemId, quantity, Long::sum);
			resolved.add(new ShelfOperations.Pick(Php.intval(pick.get("slot_id")), item.longValue("finished_product_id"), quantity));
		}
		for (Row item : items) {
			long picked = perItem.getOrDefault(item.id(), 0L);
			if (picked != Php.intval(item.get("quantity"))) {
				throw new BusinessRuleException(productName(products, item.longValue("finished_product_id")) + " için " + item.get("quantity")
					+ " adet gerekiyor, " + picked + " adet seçildi.");
			}
		}
		return resolved;
	}

	/** Sevkiyat: siseler fabrikadan cikar ({@code total_stock} duser); raf stogu hazirlamada zaten dusmustu. */
	private void releaseTotalStock(long orderId) {
		for (Row item : db.whereIn("order_items", "order_id", List.of(orderId), null)) {
			Row product = db.find("finished_products", item.longValue("finished_product_id")).orElse(null);
			if (product == null) {
				continue;
			}
			long quantity = Php.intval(item.get("quantity"));
			if (Php.intval(product.get("total_stock")) < quantity) {
				throw new BusinessRuleException("'" + product.string("name") + "' için fabrika stoğu (" + product.get("total_stock") + ") "
					+ "sevk edilecek adetten (" + item.get("quantity") + ") az; kayıtlar tutarsız.");
			}
			db.execute("update finished_products set total_stock = total_stock - ?, updated_at = ? where id = ?", quantity, Timestamps.now(),
				product.id());
		}
	}

	/** Rafta o urunun en eski giris zamani ({@code min(moved_at)} metni); hic giris yoksa en sona. */
	private String firstStockInAt(long slotId, long productId) {
		return db.first("select min(moved_at) as first from finished_product_stock_movements where finished_product_storage_slot_id = ?"
				+ " and finished_product_id = ? and movement_type in (?, ?, ?)", slotId, productId, "stock_in", "transfer_in", "order_return")
			.map(r -> r.get("first"))
			.map(v -> v instanceof LocalDateTime t ? SQL_TIME.format(t) : Php.strval(v))
			.orElse("9999-12-31 23:59:59");
	}

	// ---- yardimcilar -----------------------------------------------------------------------

	private void createItems(long orderId, Object items) {
		for (Map<String, Object> item : items(items)) {
			Map<String, Object> values = new LinkedHashMap<>(item);
			values.put("order_id", orderId);
			db.insert("order_items", values);
		}
	}

	private void recordStatusChange(long orderId, String from, String to, long userId, Object note) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("from_status", from);
		values.put("to_status", to);
		values.put("changed_by_user_id", userId);
		values.put("note", note);
		values.put("changed_at", Timestamps.now());
		values.put("order_id", orderId);
		db.insert("order_status_histories", values);
	}

	private void assertDeliveryAddressBelongsToCustomer(Row order) {
		if (db.count("select count(*) from customer_addresses where customer_id = ? and customer_id is not null and id = ?",
			order.get("customer_id"), order.get("delivery_address_id")) == 0) {
			throw new BusinessRuleException("Teslimat adresi bu müşteriye ait değil.");
		}
	}

	private static String productName(Map<Long, Row> products, long productId) {
		Row product = products.get(productId);
		return product != null && product.get("name") != null ? product.string("name") : "#" + productId;
	}

	/** {@code date} cast'inin yazdigi deger. */
	private static LocalDateTime date(Object value) {
		return value == null ? null : PhpDates.parse(Php.strval(value)).orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>((Collection<Map<String, Object>>) m.values());
		}
		return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

}
