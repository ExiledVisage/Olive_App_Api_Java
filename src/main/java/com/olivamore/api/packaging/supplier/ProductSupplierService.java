package com.olivamore.api.packaging.supplier;

import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductSupplierService}: ambalaj ve satin alinan malzemeyi getiren firmalar. */
@Service
class ProductSupplierService {

	private static final String LOTS_COUNT = "(select count(*) from packaging_lots"
		+ " where product_suppliers.id = packaging_lots.product_supplier_id) as lots_count";

	private final Db db;

	ProductSupplierService(Db db) {
		this.db = db;
	}

	/** {@code getAll}: ad veya kod aramasi, {@code isActive}; ada gore; lot sayisi yuklu. */
	List<Row> all(String search, Boolean isActive) {
		StringBuilder sql = new StringBuilder("select product_suppliers.*, " + LOTS_COUNT + " from product_suppliers");
		List<String> where = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (isActive != null) {
			where.add("is_active = ?");
			args.add(isActive);
		}
		String term = search == null ? "" : search.trim();
		if (!term.isEmpty()) {
			where.add("(name::text ilike ? or code::text ilike ?)");
			args.add("%" + term + "%");
			args.add("%" + term + "%");
		}
		if (!where.isEmpty()) {
			sql.append(" where ").append(String.join(" and ", where));
		}
		sql.append(" order by name asc");
		return db.select(sql.toString(), args.toArray());
	}

	/** {@code getById}: lot sayisi yuklu. */
	Row byId(long id) {
		return db.first("select product_suppliers.*, " + LOTS_COUNT + " from product_suppliers where id = ?", id).orElseThrow();
	}

	/** Tedarikcinin lotlari, yeniden eskiye. */
	List<Row> lots(long id) {
		return db.select("select * from packaging_lots where product_supplier_id = ? order by received_at desc, id desc", id);
	}

	/** {@code is_active} verilmezse true. @return yeni id */
	long create(Map<String, Object> validated) {
		Map<String, Object> data = new LinkedHashMap<>(validated);
		if (data.get("is_active") == null) {
			data.put("is_active", true);
		}
		return db.insert("product_suppliers", data);
	}

	void update(Row supplier, Map<String, Object> validated) {
		db.update("product_suppliers", supplier.id(), validated);
	}

	/** Lotu olan tedarikci silinmez; pasife alinir. */
	void delete(Row supplier) {
		DeletionGuard.of("'" + supplier.string("name") + "' tedarikçisi")
			.usedBy("ambalaj lotu", db.count("select count(*) from packaging_lots where product_supplier_id = ?", supplier.id()))
			.check("Pasife alabilirsiniz.");
		db.delete("product_suppliers", supplier.id());
	}

}
