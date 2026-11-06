package com.olivamore.api.inventory;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Laravel {@code StorageUnitController} + {@code StorageUnitService}: tanklar, izinli urunler/tipler,
 * attribute kurallari, bakiyeler ve hareket listesi.
 */
@RestController
class StorageUnitController {

	private static final String TABLE = "storage_units";
	private static final String MODEL = "StorageUnit";
	private static final List<String> APPLY_MODES = List.of("incoming_only", "tank_average_only", "incoming_and_average");
	private static final List<String> FLAGS = List.of("is_blend_tank", "allow_multiple_production_product_types",
		"allow_multiple_production_products");

	private final Db db;
	private final LaravelValidator validator;
	private final InventoryReads reads;

	StorageUnitController(Db db, LaravelValidator validator, InventoryReads reads) {
		this.db = db;
		this.validator = validator;
		this.reads = reads;
	}

	// ---- okuma (storage_unit.view) -----------------------------------------

	/** {@code latest()}: created_at desc. */
	@GetMapping("/api/storage-units")
	@RequiresPermission("storage_unit.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(reads.storageUnits(db.select("select * from storage_units order by created_at desc")));
	}

	@GetMapping("/api/storage-units/{storage_unit}")
	@RequiresPermission("storage_unit.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "storage_unit", table = TABLE, model = MODEL) Row unit) {
		return DataResponse.of(reads.storageUnit(unit));
	}

	/** {@code movements()} en yeni once (moved_at desc, id desc); {@code per_page} varsa sayfali. */
	@GetMapping("/api/storage-units/{storageUnit}/movements")
	@RequiresPermission("storage_unit.view")
	ListResponse<Map<String, Object>> movements(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		HttpServletRequest request) {
		String where = " from storage_movements where storage_movements.storage_unit_id = ?"
			+ " and storage_movements.storage_unit_id is not null";
		String order = " order by moved_at desc, id desc";
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(reads.movements(db.select("select *" + where + order, unit.id())));
		}
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate" + where, unit.id());
		List<Row> rows = total == 0 ? List.of()
			: db.select("select *" + where + order + " limit " + perPage.get() + " offset " + (long) (page - 1) * perPage.get(), unit.id());
		return LaravelPaginator.paginate(reads.movements(rows), total, page, perPage.get(), request);
	}

	@GetMapping("/api/storage-units/{storageUnit}/balances")
	@RequiresPermission("storage_unit.view")
	ListResponse<Map<String, Object>> balances(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit) {
		return new ListResponse.Plain<>(reads.balances(db.select("select * from storage_unit_balances"
			+ " where storage_unit_balances.storage_unit_id = ? and storage_unit_balances.storage_unit_id is not null", unit.id())));
	}

	// ---- yazma (storage_unit.manage) ---------------------------------------

	/** Bayraklar verilmediyse (ya da null) false, is_active true. Cevap 201. */
	@PostMapping("/api/storage-units")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255")
			.field("code", "required|string|max:255|unique:storage_units,code")
			.field("capacity", "required|numeric|gt:0")
			.field("capacity_unit", "required|string|in:KG,LITRE")
			.field("is_blend_tank", "nullable|boolean")
			.field("allow_multiple_production_product_types", "nullable|boolean")
			.field("allow_multiple_production_products", "nullable|boolean")
			.field("description", "nullable|string")
			.field("is_active", "nullable|boolean"));
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("name", v.get("name"));
		values.put("code", v.get("code"));
		values.put("capacity", v.get("capacity"));
		values.put("capacity_unit", v.get("capacity_unit"));
		for (String flag : FLAGS) {
			values.put(flag, v.get(flag) == null ? Boolean.FALSE : v.get(flag));
		}
		values.put("description", v.get("description"));
		values.put("is_active", v.get("is_active") == null ? Boolean.TRUE : v.get("is_active"));
		return DataResponse.created(reads.storageUnit(db.create(TABLE, values)));
	}

	/** Yalnizca gonderilen alanlar (null dahil: NOT NULL kolonda Laravel'deki gibi veritabani hatasi). */
	@RequestMapping(path = "/api/storage-units/{storage_unit}", method = {RequestMethod.PATCH, RequestMethod.PUT})
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "storage_unit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("code", "sometimes|string|max:255|unique:storage_units,code," + unit.id())
			.field("capacity", "sometimes|numeric|gt:0")
			.field("capacity_unit", "sometimes|string|in:KG,LITRE")
			.field("is_blend_tank", "nullable|boolean")
			.field("allow_multiple_production_product_types", "nullable|boolean")
			.field("allow_multiple_production_products", "nullable|boolean")
			.field("description", "nullable|string")
			.field("is_active", "sometimes|boolean"));
		if (!v.all().isEmpty()) {
			db.update(TABLE, unit.id(), v.all());
		}
		return DataResponse.of(reads.storageUnit(db.find(TABLE, unit.id()).orElseThrow()));
	}

	/** Icinde aktif materyal ya da hareket kaydi olan tank silinmez (pasife alinir). Cevap 204. */
	@DeleteMapping("/api/storage-units/{storage_unit}")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	ResponseEntity<Void> destroy(@Bound(value = "storage_unit", table = TABLE, model = MODEL) Row unit) {
		long movementCount = db.count("select count(*) as aggregate from storage_movements"
			+ " where storage_movements.storage_unit_id = ? and storage_movements.storage_unit_id is not null", unit.id());
		long materialCount = db.count("select count(*) as aggregate from materials where current_location_type = ?"
			+ " and current_location_id = ? and status = ?", "storage_unit", unit.id(), "active");
		if (materialCount > 0) {
			throw new BusinessRuleException("'" + unit.string("code") + "' tankında " + materialCount + " aktif materyal var; "
				+ "silinemez. Önce tankı boşaltın.");
		}
		if (movementCount > 0) {
			throw new BusinessRuleException("'" + unit.string("code") + "' tankının " + movementCount + " hareket kaydı var; "
				+ "silinemez. Tankı kullanımdan kaldırmak için is_active alanını false yapın.");
		}
		db.delete(TABLE, unit.id());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/api/storage-units/{storageUnit}/allowed-production-products")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> syncAllowedProducts(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("production_product_ids", "required|array")
			.field("production_product_ids.*", "integer|exists:production_products,id"));
		sync(unit, "storage_unit_allowed_production_products", "production_product_id", v.get("production_product_ids"));
		return DataResponse.of(reads.storageUnit(unit));
	}

	@PutMapping("/api/storage-units/{storageUnit}/allowed-production-product-types")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> syncAllowedTypes(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("production_product_type_ids", "required|array")
			.field("production_product_type_ids.*", "integer|exists:production_product_types,id"));
		sync(unit, "storage_unit_allowed_production_product_types", "production_product_type_id", v.get("production_product_type_ids"));
		return DataResponse.of(reads.storageUnit(unit));
	}

	/**
	 * Listede olmayan kurallar silinir, digerleri updateOrCreate. Kural sadece decimal attribute icin;
	 * min > max olamaz. Hata olursa (sonraki kuralda bile) hepsi geri alinir.
	 */
	@PutMapping("/api/storage-units/{storageUnit}/attribute-rules")
	@RequiresPermission("storage_unit.manage")
	@Transactional
	DataResponse<Map<String, Object>> syncAttributeRules(@Bound(value = "storageUnit", table = TABLE, model = MODEL) Row unit,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("rules", "required|array")
			.field("rules.*.production_product_attribute_definition_id",
				"required|integer|exists:production_product_attribute_definitions,id")
			.field("rules.*.min_value_decimal", "nullable|numeric")
			.field("rules.*.max_value_decimal", "nullable|numeric")
			.field("rules.*.apply_mode", "nullable|string|in:incoming_only,tank_average_only,incoming_and_average")
			.field("rules.*.is_active", "nullable|boolean")
			.field("rules.*.note", "nullable|string"));

		List<Map<String, Object>> rules = new ArrayList<>();
		for (Object item : list(v.get("rules"))) {
			@SuppressWarnings("unchecked")
			Map<String, Object> rule = (Map<String, Object>) item;
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("production_product_attribute_definition_id", Php.intval(rule.get("production_product_attribute_definition_id")));
			r.put("min_value_decimal", rule.get("min_value_decimal"));
			r.put("max_value_decimal", rule.get("max_value_decimal"));
			r.put("apply_mode", rule.get("apply_mode") == null ? "incoming_and_average" : rule.get("apply_mode"));
			r.put("is_active", rule.get("is_active") == null ? Boolean.TRUE : rule.get("is_active"));
			r.put("note", rule.get("note"));
			rules.add(r);
		}
		Set<Long> definitionIds = new LinkedHashSet<>();
		rules.forEach(r -> definitionIds.add((Long) r.get("production_product_attribute_definition_id")));

		// ProductionProductAttributeDefinition::with('attributeDefinition'): data_type ve label eslemenin attribute'undan
		Map<Long, Row> definitions = new LinkedHashMap<>();
		for (Row d : db.select("select m.id, a.label, a.data_type from production_product_attribute_definitions m"
			+ " left join attribute_definitions a on a.id = m.attribute_definition_id where m.id in ("
			+ Relations.inList(definitionIds) + ")")) {
			definitions.put(d.id(), d);
		}

		db.execute("delete from storage_unit_attribute_rules where storage_unit_attribute_rules.storage_unit_id = ?"
			+ " and storage_unit_attribute_rules.storage_unit_id is not null"
			+ " and production_product_attribute_definition_id not in ("
			+ Relations.inList(definitionIds) + ")", unit.id());

		for (Map<String, Object> rule : rules) {
			Row definition = definitions.get((Long) rule.get("production_product_attribute_definition_id"));
			if (definition != null && !"decimal".equals(definition.get("data_type"))) {
				throw new BusinessRuleException("Tank attribute rule sadece decimal attribute'lar için tanımlanabilir. "
					+ "'" + Php.strval(definition.get("label")) + "' attribute'unun tipi: " + Php.strval(definition.get("data_type")) + ".");
			}
			Object min = rule.get("min_value_decimal");
			Object max = rule.get("max_value_decimal");
			if (min != null && max != null && Php.floatval(min) > Php.floatval(max)) {
				throw new BusinessRuleException("Attribute rule içinde min değer max değerden büyük olamaz.");
			}
			if (!APPLY_MODES.contains(rule.get("apply_mode"))) {
				throw new BusinessRuleException("Geçersiz attribute rule apply_mode gönderildi.");
			}
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("min_value_decimal", min);
			values.put("max_value_decimal", max);
			values.put("apply_mode", rule.get("apply_mode"));
			values.put("is_active", rule.get("is_active"));
			values.put("note", rule.get("note"));
			Optional<Row> existing = db.first("select * from storage_unit_attribute_rules"
				+ " where storage_unit_attribute_rules.storage_unit_id = ? and storage_unit_attribute_rules.storage_unit_id is not null"
				+ " and production_product_attribute_definition_id = ? limit 1",
				unit.id(), rule.get("production_product_attribute_definition_id"));
			if (existing.isPresent()) {
				db.update("storage_unit_attribute_rules", existing.get().id(), values);
			}
			else {
				Map<String, Object> created = new LinkedHashMap<>();
				created.put("production_product_attribute_definition_id", rule.get("production_product_attribute_definition_id"));
				created.putAll(values);
				created.put("storage_unit_id", unit.id());
				db.insert("storage_unit_attribute_rules", created);
			}
		}
		return DataResponse.of(reads.storageUnit(unit));
	}

	// ---- yardimcilar -------------------------------------------------------

	/** syncAllowed*: (int) + tekrarsiz; listede olmayanlar silinir, eksikler (istek sirasiyla) eklenir. */
	private void sync(Row unit, String table, String column, Object requested) {
		LinkedHashSet<Long> ids = new LinkedHashSet<>();
		for (Object id : list(requested)) {
			ids.add(Php.intval(id));
		}
		String in = Relations.inList(ids);
		db.execute("delete from " + table + " where " + table + ".storage_unit_id = ? and " + table + ".storage_unit_id is not null"
			+ (in.isEmpty() ? "" : " and " + column + " not in (" + in + ")"), unit.id());
		Set<Long> existing = new LinkedHashSet<>();
		for (Row r : db.select("select " + column + " from " + table + " where " + table + ".storage_unit_id = ?"
			+ " and " + table + ".storage_unit_id is not null", unit.id())) {
			existing.add(Php.intval(r.get(column)));
		}
		for (Long id : ids) {
			if (!existing.contains(id)) {
				Map<String, Object> values = new LinkedHashMap<>();
				values.put(column, id);
				values.put("storage_unit_id", unit.id());
				db.insert(table, values);
			}
		}
	}

	private static List<?> list(Object value) {
		if (value instanceof List<?> l) {
			return l;
		}
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>(m.values());
		}
		return List.of();
	}

}
