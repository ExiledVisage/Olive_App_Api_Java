package com.olivamore.api.sales.order;

import com.olivamore.api.sales.order.StockCheckResponse.Location;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bitmis urun ve depo tablolarina erisim: siparis hazirlama ve stok kontrolunun SQL tarafi. */
@Component
class OrderStockQueries {

	record ProductRow(Long id, String name, BigDecimal volumeLitre, long totalStock) {
	}

	record SlotStock(Long slotId, long quantity) {
	}

	private record TotalRow(Long productId, long total) {
	}

	private record LocationRow(Long productId, Long storageId, String storageName, Long slotId, String slotName,
		long quantity) {
	}

	private static final Comparator<String> NAME_ORDER = OrderStockQueries::compareNames;

	private final JdbcClient jdbc;

	OrderStockQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Map<Long, ProductRow> products(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, volume_litre, total_stock from finished_products where id in (:ids)")
			.param("ids", present).query(ProductRow.class).list().stream()
			.collect(Collectors.toMap(ProductRow::id, Function.identity()));
	}

	Set<Long> existingSlots(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("select id from finished_product_storage_slots where id in (:ids)")
			.param("ids", present).query(Long.class).list());
	}

	Map<Long, Long> inStorages(Collection<Long> productIds) {
		return totals("""
			select finished_product_id as product_id, sum(quantity) as total
			from finished_product_storage_stocks where finished_product_id in (:ids)
			group by finished_product_id
			""", productIds, 0);
	}

	/** Baska hazir siparislerin borcu. */
	Map<Long, Long> committed(Collection<Long> productIds, long exceptOrderId) {
		return totals("""
			select i.finished_product_id as product_id, sum(i.quantity) as total
			from order_items i join orders o on o.id = i.order_id
			where i.finished_product_id in (:ids) and o.status = 'ready' and o.id <> :except
			group by i.finished_product_id
			""", productIds, exceptOrderId);
	}

	/** Raftan alinmis, sevk bekleyen adetler. */
	Map<Long, Long> awaitingShipment(Collection<Long> productIds) {
		return totals("""
			select m.finished_product_id as product_id, sum(m.quantity) as total
			from finished_product_stock_movements m join orders o on o.id = m.order_id
			where m.finished_product_id in (:ids) and m.movement_type = 'order_pick' and o.status = 'ready'
			group by m.finished_product_id
			""", productIds, 0);
	}

	private Map<Long, Long> totals(String sql, Collection<Long> productIds, long exceptOrderId) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		var spec = jdbc.sql(sql).param("ids", present);
		if (sql.contains(":except")) {
			spec = spec.param("except", exceptOrderId);
		}
		return spec.query(TotalRow.class).list().stream()
			.collect(Collectors.toMap(TotalRow::productId, TotalRow::total));
	}

	/** Urunun bulundugu raflar: depo adi, raf adi, raf id sirasinda. */
	Map<Long, List<Location>> locations(Collection<Long> productIds) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		Comparator<LocationRow> order = Comparator
			.comparing(LocationRow::storageName, NAME_ORDER)
			.thenComparing(LocationRow::slotName, NAME_ORDER)
			.thenComparing(LocationRow::slotId);
		return jdbc.sql("""
			select s.finished_product_id as product_id, st.id as storage_id, st.name as storage_name,
				sl.id as slot_id, sl.name as slot_name, s.quantity
			from finished_product_storage_slot_stocks s
			join finished_product_storage_slots sl on sl.id = s.finished_product_storage_slot_id
			join finished_product_storages st on st.id = sl.finished_product_storage_id
			where s.finished_product_id in (:ids) and s.quantity > 0
			""").param("ids", present).query(LocationRow.class).list().stream()
			.sorted(order)
			.collect(Collectors.groupingBy(LocationRow::productId, Collectors.mapping(
				row -> new Location(row.storageId(), row.storageName(), row.slotId(), row.slotName(), row.quantity()),
				Collectors.toList())));
	}

	/** Rafa en eski giristen baslayarak; hic giris kaydi olmayan raflar en sonda. */
	List<SlotStock> shelvesFifo(long productId) {
		return jdbc.sql("""
			select s.finished_product_storage_slot_id as slot_id, s.quantity
			from finished_product_storage_slot_stocks s
			where s.finished_product_id = :product and s.quantity > 0
			order by (select min(m.moved_at) from finished_product_stock_movements m
				where m.finished_product_storage_slot_id = s.finished_product_storage_slot_id
				and m.finished_product_id = s.finished_product_id
				and m.movement_type in ('stock_in', 'transfer_in', 'order_return')) asc nulls last, s.id
			""").param("product", productId).query(SlotStock.class).list();
	}

	/** Sevkiyat: siseler fabrikadan cikar; raf stogu hazirlamada zaten dusmustu. */
	void releaseTotalStock(ProductRow product, int quantity) {
		if (product.totalStock() < quantity) {
			throw new BusinessRuleException("'" + product.name() + "' için fabrika stoğu (" + product.totalStock()
				+ ") sevk edilecek adetten (" + quantity + ") az; kayıtlar tutarsız.");
		}
		jdbc.sql("update finished_products set total_stock = total_stock - ?, updated_at = ? where id = ?")
			.params(quantity, Timestamps.now(), product.id())
			.update();
	}

	/** Ikisi de sayisal adsa sayi olarak, degilse harf sirasiyla. */
	private static int compareNames(String left, String right) {
		String a = left == null ? "" : left;
		String b = right == null ? "" : right;
		try {
			return new BigDecimal(a.strip()).compareTo(new BigDecimal(b.strip()));
		}
		catch (NumberFormatException e) {
			return a.compareTo(b);
		}
	}

}
