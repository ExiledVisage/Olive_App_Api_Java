package com.olivamore.api.packaging;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ambalaj kalemleri, lotlar ve kosu ayirmalari: diger modullerin okudugu tek kapi. */
@Component
public class PackagingDirectory {

	public record Item(Long id, String name, String type) {
	}

	public record RunPackaging(Long id, Long runId, Long packagingItemId, String packagingItemName,
		String packagingItemType, Long packagingLotId, String lotCode, LocalDateTime lotReceivedAt,
		Integer lotRemaining, String supplierName, int quantity) {
	}

	private final JdbcClient jdbc;

	PackagingDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, Item> items(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, type from packaging_items where id in (:ids)").param("ids", present)
			.query(Item.class).list().stream().collect(Collectors.toMap(Item::id, Function.identity()));
	}

	/** Kalem yoksa bos metin. */
	public String itemName(long id) {
		return jdbc.sql("select name from packaging_items where id = :id").param("id", id)
			.query(String.class).optional().orElse("");
	}

	public List<Long> existingLots(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select id from packaging_lots where id in (:ids)").param("ids", present)
			.query(Long.class).list();
	}

	public boolean hasAllocations(long runId) {
		return jdbc.sql("select count(*) from finished_product_run_packagings where finished_product_run_id = :id")
			.param("id", runId).query(Long.class).single() > 0;
	}

	/** Kosularin ambalaj ayirmalari, kalem, lot ve tedarikci bilgileriyle, kayit sirasinda. */
	public List<RunPackaging> runPackagings(Collection<Long> runIds) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select p.id, p.finished_product_run_id as run_id, p.packaging_item_id, i.name as packaging_item_name,
				i.type as packaging_item_type, p.packaging_lot_id, l.lot_code, l.received_at as lot_received_at,
				l.quantity_remaining as lot_remaining, s.name as supplier_name, p.quantity
			from finished_product_run_packagings p
			left join packaging_items i on i.id = p.packaging_item_id
			left join packaging_lots l on l.id = p.packaging_lot_id
			left join product_suppliers s on s.id = l.product_supplier_id
			where p.finished_product_run_id in (:ids)
			order by p.id
			""").param("ids", runIds).query(RunPackaging.class).list();
	}

}
