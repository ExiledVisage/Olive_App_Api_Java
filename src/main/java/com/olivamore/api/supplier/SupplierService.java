package com.olivamore.api.supplier;

import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code SupplierService}: tedarikciler ve tarlalari. */
@Service
class SupplierService {

	static final String FIELD_NOT_OF_SUPPLIER = "Bu tarla bu tedarikçiye ait değil.";

	private final Db db;

	SupplierService(Db db) {
		this.db = db;
	}

	/**
	 * @param isActive null ise filtre yok
	 * @param search   ad, kod ya da tarla kodunda buyuk/kucuk harfe duyarsiz (whereLike -> ilike); bos ise yok
	 */
	List<Row> all(Boolean isActive, String search) {
		StringBuilder sql = new StringBuilder("""
			select suppliers.*, (select count(*) from production_batches
			  where suppliers.id = production_batches.supplier_id) as production_batches_count
			from suppliers""");
		List<Object> args = new ArrayList<>();
		List<String> where = new ArrayList<>();
		if (isActive != null) {
			where.add("is_active = ?");
			args.add(isActive);
		}
		if (!search.isEmpty()) {
			String like = "%" + search + "%";
			where.add("(name::text ilike ? or code::text ilike ? or exists (select * from supplier_fields"
				+ " where suppliers.id = supplier_fields.supplier_id and code::text ilike ?))");
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (!where.isEmpty()) {
			sql.append(" where ").append(String.join(" and ", where));
		}
		sql.append(" order by name asc");
		return db.select(sql.toString(), args.toArray());
	}

	/** Tedarikci ve (varsa) tarlalari birlikte. */
	Row create(Map<String, Object> validated) {
		Map<String, Object> data = new LinkedHashMap<>(validated);
		Object fields = data.remove("fields");
		Row supplier = db.create("suppliers", data);
		for (Map<String, Object> field : items(fields)) {
			Map<String, Object> values = new LinkedHashMap<>(field);
			values.put("supplier_id", supplier.id());
			db.insert("supplier_fields", values);
		}
		return supplier;
	}

	Row update(Row supplier, Map<String, Object> validated) {
		db.update("suppliers", supplier.id(), validated);
		return db.find("suppliers", supplier.id()).orElseThrow();
	}

	void delete(Row supplier) {
		DeletionGuard.of("'" + supplier.string("name") + "' tedarikçisi")
			.usedBy("üretim partisi", db.count("select count(*) from production_batches where supplier_id = ?", supplier.id()))
			.check("Pasife alabilirsiniz.");
		db.delete("suppliers", supplier.id());
	}

	Row createField(Row supplier, Map<String, Object> validated) {
		Map<String, Object> values = new LinkedHashMap<>(validated);
		values.put("supplier_id", supplier.id());
		return db.create("supplier_fields", values);
	}

	Row updateField(Row field, Map<String, Object> validated) {
		db.update("supplier_fields", field.id(), validated);
		return db.find("supplier_fields", field.id()).orElseThrow();
	}

	void deleteField(Row field) {
		DeletionGuard.of("'" + field.string("code") + "' tarlası")
			.usedBy("üretim partisi", db.count("select count(*) from production_batches where supplier_field_id = ?", field.id()))
			.check("Pasife alabilirsiniz.");
		db.delete("supplier_fields", field.id());
	}

	static void ensureFieldOf(Row supplier, Row field) {
		if (field.longValue("supplier_id") == null || field.longValue("supplier_id") != supplier.id()) {
			throw new HttpStatusException(404, FIELD_NOT_OF_SUPPLIER);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
		if (value instanceof List<?> list) {
			return list.stream().map(i -> i instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
		}
		if (value instanceof Map<?, ?> map) {
			return ((Map<String, Object>) map).values().stream()
				.map(i -> i instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
		}
		return List.of();
	}

}
