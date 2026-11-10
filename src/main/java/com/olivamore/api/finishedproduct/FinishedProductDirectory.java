package com.olivamore.api.finishedproduct;

import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bitmis urunler, raf stoklari ve kosu etiketleri: diger modullerin okudugu tek kapi. */
@Component
public class FinishedProductDirectory {

	public record Product(Long id, String name, BigDecimal volumeLitre, long totalStock, boolean isActive,
		LocalDateTime createdAt, LocalDateTime updatedAt) {
	}

	public record ShelfLocation(Long productId, Long storageId, String storageName, Long slotId, String slotName,
		long quantity) {
	}

	public record SlotStock(Long slotId, long quantity) {
	}

	private record TotalRow(Long productId, long total) {
	}

	private record PickRow(Long productId, Long orderId, long quantity) {
	}

	private record RecipeLink(Long id, Long finishedProductRecipeId) {
	}

	private final JdbcClient jdbc;
	private final OrderDirectory orders;

	FinishedProductDirectory(JdbcClient jdbc, OrderDirectory orders) {
		this.jdbc = jdbc;
		this.orders = orders;
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, Product> products(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select id, name, volume_litre, total_stock, is_active, created_at, updated_at
			from finished_products where id in (:ids)
			""").param("ids", present).query(Product.class).list().stream()
			.collect(Collectors.toMap(Product::id, Function.identity()));
	}

	public Set<Long> existingSlots(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("select id from finished_product_storage_slots where id in (:ids)")
			.param("ids", present).query(Long.class).list());
	}

	/** Urunlerin depolardaki toplam adedi; hic stogu olmayan urun haritada yok. */
	public Map<Long, Long> inStorages(Collection<Long> productIds) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select finished_product_id as product_id, sum(quantity) as total
			from finished_product_storage_stocks where finished_product_id in (:ids)
			group by finished_product_id
			""").param("ids", present).query(TotalRow.class).list().stream()
			.collect(Collectors.toMap(TotalRow::productId, TotalRow::total));
	}

	/** Hazir siparisler icin raftan alinmis, sevk bekleyen adetler; sifir olanlar haritada yok. */
	public Map<Long, Long> awaitingShipment(Collection<Long> productIds) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		List<PickRow> picks = jdbc.sql("""
			select finished_product_id as product_id, order_id, sum(quantity) as quantity
			from finished_product_stock_movements
			where finished_product_id in (:ids) and movement_type = 'order_pick' and order_id is not null
			group by finished_product_id, order_id
			""").param("ids", present).query(PickRow.class).list();
		if (picks.isEmpty()) {
			return Map.of();
		}
		Set<Long> ready = orders.readyAmong(picks.stream().map(PickRow::orderId).distinct().toList());
		Map<Long, Long> totals = new HashMap<>();
		picks.stream().filter(pick -> ready.contains(pick.orderId()))
			.forEach(pick -> totals.merge(pick.productId(), pick.quantity(), Long::sum));
		return totals;
	}

	/** Urunlerin bulundugu raflar; sira verilmez. */
	public List<ShelfLocation> shelfLocations(Collection<Long> productIds) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select s.finished_product_id as product_id, st.id as storage_id, st.name as storage_name,
				sl.id as slot_id, sl.name as slot_name, s.quantity
			from finished_product_storage_slot_stocks s
			join finished_product_storage_slots sl on sl.id = s.finished_product_storage_slot_id
			join finished_product_storages st on st.id = sl.finished_product_storage_id
			where s.finished_product_id in (:ids) and s.quantity > 0
			""").param("ids", present).query(ShelfLocation.class).list();
	}

	/** Rafa en eski giristen baslayarak; hic giris kaydi olmayan raflar en sonda. */
	public List<SlotStock> shelvesFifo(long productId) {
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

	/** Sevk edilen siseler fabrika stogundan duser. */
	public void releaseTotalStock(long productId, int quantity) {
		jdbc.sql("update finished_products set total_stock = total_stock - ?, updated_at = ? where id = ?")
			.params(quantity, Timestamps.now(), productId)
			.update();
	}

	/** Urunlerin tarifleri; tarifi olmayan urun haritada yok. */
	public Map<Long, Long> recipeIdsOf(Collection<Long> productIds) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select id, finished_product_recipe_id from finished_products
			where id in (:ids) and finished_product_recipe_id is not null
			""").param("ids", present).query(RecipeLink.class).list().stream()
			.collect(Collectors.toMap(RecipeLink::id, RecipeLink::finishedProductRecipeId));
	}

	/** Bildirimde gorunen ad; kayit yoksa bos. */
	public Optional<String> productName(long id) {
		return jdbc.sql("select name from finished_products where id = :id").param("id", id)
			.query(String.class).optional();
	}

	/** Bildirimde gorunen "urun - adet" etiketi; kosu yoksa bos. */
	public Optional<String> runLabel(long id) {
		return jdbc.sql("""
			select coalesce(p.name, 'Ürün') || ' - ' || r.quantity || ' adet'
			from finished_product_runs r left join finished_products p on p.id = r.finished_product_id
			where r.id = :id
			""").param("id", id).query(String.class).optional();
	}

}
