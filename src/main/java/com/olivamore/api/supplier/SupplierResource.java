package com.olivamore.api.supplier;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code SupplierResource} ve {@code SupplierFieldResource}. */
@Component
class SupplierResource {

	private final Db db;

	SupplierResource(Db db) {
		this.db = db;
	}

	/** {@code getById}: tarlalar + parti sayisi; tedarikci satiri oldugu gibi (create sonrasi verilen alanlar). */
	Map<String, Object> detail(Row supplier) {
		long count = db.count("select count(*) from production_batches where supplier_id = ?", supplier.id());
		Map<String, Object> values = new LinkedHashMap<>(supplier.values());
		values.put("production_batches_count", count);
		return suppliers(List.of(new Row(values))).getFirst();
	}

	/** Tarlalar Laravel'in eager loading sorgusuyla (siralamasiz). */
	List<Map<String, Object>> suppliers(List<Row> suppliers) {
		Map<Long, List<Map<String, Object>>> fields = new LinkedHashMap<>();
		for (Row f : db.whereIn("supplier_fields", "supplier_id", Relations.ids(suppliers), null)) {
			fields.computeIfAbsent(f.longValue("supplier_id"), k -> new ArrayList<>()).add(field(f));
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row s : suppliers) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", s.get("id"));
			r.put("name", s.get("name"));
			r.put("code", s.get("code"));
			r.put("phone", s.get("phone"));
			r.put("email", s.get("email"));
			r.put("address", s.get("address"));
			r.put("note", s.get("note"));
			r.put("is_active", Casts.bool(s.get("is_active")));
			r.put("delivery_count", s.get("production_batches_count"));
			r.put("fields", fields.getOrDefault(s.id(), List.of()));
			r.put("created_at", s.get("created_at"));
			r.put("updated_at", s.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** SupplierFieldResource ({@code area_decare}: decimal:2). */
	static Map<String, Object> field(Row f) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", f.get("id"));
		r.put("supplier_id", f.get("supplier_id"));
		r.put("code", f.get("code"));
		r.put("location", f.get("location"));
		Object area = f.get("area_decare");
		r.put("area_decare", area == null ? null : new BigDecimal(Php.strval(area).strip()).setScale(2, RoundingMode.HALF_UP).toPlainString());
		r.put("note", f.get("note"));
		r.put("is_active", Casts.bool(f.get("is_active")));
		return r;
	}

}
