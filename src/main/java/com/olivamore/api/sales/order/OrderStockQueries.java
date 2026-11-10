package com.olivamore.api.sales.order;

import com.olivamore.api.finishedproduct.FinishedProductDirectory;
import com.olivamore.api.sales.order.StockCheckResponse.Location;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Siparis hazirlama ve stok kontrolunun okumalari: urun ve raf bilgileri bitmis urun modulunden. */
@Component
class OrderStockQueries {

	record ProductRow(Long id, String name, BigDecimal volumeLitre, long totalStock) {
	}

	record SlotStock(Long slotId, long quantity) {
	}

	private record TotalRow(Long productId, long total) {
	}

	private static final Comparator<String> NAME_ORDER = OrderStockQueries::compareNames;

	private final JdbcClient jdbc;
	private final FinishedProductDirectory directory;

	OrderStockQueries(JdbcClient jdbc, FinishedProductDirectory directory) {
		this.jdbc = jdbc;
		this.directory = directory;
	}

	Map<Long, ProductRow> products(Collection<Long> ids) {
		return directory.products(ids).values().stream().collect(Collectors.toMap(FinishedProductDirectory.Product::id,
			p -> new ProductRow(p.id(), p.name(), p.volumeLitre(), p.totalStock())));
	}

	Set<Long> existingSlots(Collection<Long> ids) {
		return directory.existingSlots(ids);
	}

	Map<Long, Long> inStorages(Collection<Long> productIds) {
		return directory.inStorages(productIds);
	}

	/** Baska hazir siparislerin borcu. */
	Map<Long, Long> committed(Collection<Long> productIds, long exceptOrderId) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select i.finished_product_id as product_id, sum(i.quantity) as total
			from order_items i join orders o on o.id = i.order_id
			where i.finished_product_id in (:ids) and o.status = 'ready' and o.id <> :except
			group by i.finished_product_id
			""").param("ids", present).param("except", exceptOrderId).query(TotalRow.class).list().stream()
			.collect(Collectors.toMap(TotalRow::productId, TotalRow::total));
	}

	/** Raftan alinmis, sevk bekleyen adetler. */
	Map<Long, Long> awaitingShipment(Collection<Long> productIds) {
		return directory.awaitingShipment(productIds);
	}

	/** Urunun bulundugu raflar: depo adi, raf adi, raf id sirasinda. */
	Map<Long, List<Location>> locations(Collection<Long> productIds) {
		Comparator<FinishedProductDirectory.ShelfLocation> order = Comparator
			.comparing(FinishedProductDirectory.ShelfLocation::storageName, NAME_ORDER)
			.thenComparing(FinishedProductDirectory.ShelfLocation::slotName, NAME_ORDER)
			.thenComparing(FinishedProductDirectory.ShelfLocation::slotId);
		return directory.shelfLocations(productIds).stream()
			.sorted(order)
			.collect(Collectors.groupingBy(FinishedProductDirectory.ShelfLocation::productId, Collectors.mapping(
				row -> new Location(row.storageId(), row.storageName(), row.slotId(), row.slotName(), row.quantity()),
				Collectors.toList())));
	}

	/** Rafa en eski giristen baslayarak; hic giris kaydi olmayan raflar en sonda. */
	List<SlotStock> shelvesFifo(long productId) {
		return directory.shelvesFifo(productId).stream().map(s -> new SlotStock(s.slotId(), s.quantity())).toList();
	}

	/** Sevkiyat: siseler fabrikadan cikar; raf stogu hazirlamada zaten dusmustu. */
	void releaseTotalStock(ProductRow product, int quantity) {
		if (product.totalStock() < quantity) {
			throw new BusinessRuleException("'" + product.name() + "' için fabrika stoğu (" + product.totalStock()
				+ ") sevk edilecek adetten (" + quantity + ") az; kayıtlar tutarsız.");
		}
		directory.releaseTotalStock(product.id(), quantity);
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
