package com.olivamore.api.inventory.material;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Laravel {@code MaterialService}: materyal listesi (durum, soy suzgeci, arama; en yeni 200). */
@Service
class MaterialService {

	static final int LIST_LIMIT = 200;


	private static final List<String> STATUSES = List.of("active", "consumed");

	private static final List<String> LINEAGES = List.of("root", "root_raw", "root_opening", "derived");

	private final Db db;

	MaterialService(Db db) {
		this.db = db;
	}

	/**
	 * {@code status}: verilmezse ya da bossa active; active/consumed disinda hepsi. {@code lineage}: root | root_raw |
	 * root_opening | derived. {@code search}: materyal no (#12 ya da 12), not, urun tipi adi, parti kodu, tank adi/kodu.
	 */
	Map<String, Object> index(String statusParam, String lineageParam, String searchParam) {
		// query('status', 'active'): InputBag'de `?? $default`; bos deger (null'a cevrilmis) da varsayilana duser
		String status = statusParam == null ? "active" : statusParam;
		status = status != null && STATUSES.contains(status) ? status : null;
		String lineageFilter = lineageParam;
		lineageFilter = lineageFilter != null && LINEAGES.contains(lineageFilter) ? lineageFilter : null;
		String search = searchParam == null ? "" : searchParam.trim();
		String digits = search.replaceFirst("^#+", "");
		Long id = !digits.isEmpty() && digits.chars().allMatch(c -> c >= '0' && c <= '9') ? Php.intval(digits) : null;

		StringBuilder sql = new StringBuilder("select * from materials");
		List<String> where = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (status != null) {
			where.add("status = ?");
			args.add(status);
		}
		if (lineageFilter != null) {
			switch (lineageFilter) {
				case "root" -> where.add("parent_material_id is null");
				case "root_raw" -> {
					where.add("parent_material_id is null and origin_type = ?");
					args.add("BATCH_PROCESS");
				}
				case "root_opening" -> {
					where.add("parent_material_id is null and origin_type = ?");
					args.add("STORAGE_OPENING");
				}
				default -> where.add("parent_material_id is not null");
			}
		}
		if (!search.isEmpty()) {
			String like = "%" + search + "%";
			StringBuilder or = new StringBuilder("(");
			if (id != null) {
				or.append("id = ? or ");
				args.add(id);
			}
			or.append("note::text ilike ?")
				.append(" or exists (select * from production_product_types where materials.production_product_type_id = production_product_types.id and name::text ilike ?)")
				.append(" or exists (select * from production_batches where materials.production_batch_id = production_batches.id and batch_code::text ilike ?)")
				.append(" or (current_location_type = ? and exists (select * from storage_units where materials.current_location_id = storage_units.id")
				.append(" and (name::text ilike ? or code::text ilike ?))))");
			args.addAll(List.of(like, like, like, "storage_unit", like, like));
			where.add(or.toString());
		}
		if (!where.isEmpty()) {
			sql.append(" where ").append(String.join(" and ", where));
		}
		sql.append(" order by id desc limit ").append(LIST_LIMIT);
		List<Row> materials = db.select(sql.toString(), args.toArray());

		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(materials, "production_product_type_id"), null));
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(types.values(), "production_product_id"), null));
		Map<Long, Row> batches = Relations.byId(db.whereIn("production_batches", "id",
			Relations.column(materials, "production_batch_id"), null));
		Map<Long, Row> units = Relations.byId(db.whereIn("storage_units", "id",
			Relations.column(materials, "current_location_id"), null));

		List<Map<String, Object>> data = new ArrayList<>();
		for (Row m : materials) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", m.get("id"));
			r.put("quantity", Casts.decimal(m.get("quantity"), 4));
			r.put("unit", m.get("unit"));
			r.put("status", m.get("status"));
			r.put("lineage_role", MaterialDetailResource.lineageRole(m));
			r.put("parent_material_id", m.get("parent_material_id"));
			r.put("origin_type", m.get("origin_type"));
			Row type = types.get(Relations.key(m.get("production_product_type_id")));
			r.put("production_product_type", type == null ? null
				: MaterialDetailResource.typeSummary(type, products.get(Relations.key(type.get("production_product_id")))));
			r.put("storage_unit", MaterialDetailResource.storageUnit(m, units));
			Row batch = batches.get(Relations.key(m.get("production_batch_id")));
			r.put("batch_code", batch == null ? null : batch.get("batch_code"));
			r.put("created_at", m.get("created_at"));
			data.add(r);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("data", data);
		body.put("meta", Map.of("limit", LIST_LIMIT));
		return body;
	}

}
