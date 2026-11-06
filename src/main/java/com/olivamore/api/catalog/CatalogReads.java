package com.olivamore.api.catalog;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Katalog iliskilerinin yuklenmesi (Eloquent eager loading) ve JsonResource'lar.
 * <p>
 * Iliskiler Laravel'in sorgulariyla ayni bicimde ve ayni ORDER BY ile yuklenir (siralanmayan
 * iliskilerde siralama yok): esit siralama degerli ya da siralanmayan satirlar Laravel'deki sirada gelir.
 * Resource'lar Map olarak kurulur: {@code whenLoaded} ile yuklenmemis iliskinin anahtari hic yazilmaz.
 */
@Component
class CatalogReads {

	private final Db db;

	CatalogReads(Db db) {
		this.db = db;
	}

	// ======================================================================
	// Hatlar (ProductionLineResource) ve altlari
	// ======================================================================

	/** Hangi iliskilerin yuklenecegi (Laravel'deki with([...]) listeleri). */
	record LineLoad(boolean lineProductTypes, boolean extensions, boolean extensionTypeFields) {

		/** ProductionLineService::getAll / getById */
		static final LineLoad FULL = new LineLoad(true, true, true);

		/** ProductionProductTypeService::lineRelations, ProductionProductService::getById (tip ve uzanti yok) */
		static final LineLoad BASIC = new LineLoad(false, false, false);

	}

	List<Map<String, Object>> lines(List<Row> lines, LineLoad load) {
		List<Long> ids = ids(lines);
		Map<Long, List<Map<String, Object>>> inputs = lineProducts("production_line_input_production_products", ids, load);
		Map<Long, List<Map<String, Object>>> outputs = lineProducts("production_line_output_production_products", ids, load);
		Map<Long, List<Map<String, Object>>> stages = group(stages(db.whereIn("production_stages", "production_line_id", ids,
			"stage_order asc"), load.extensions(), load.extensionTypeFields()), "production_line_id");

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row line : lines) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", line.get("id"));
			r.put("name", line.get("name"));
			r.put("description", line.get("description"));
			r.put("input_production_products", inputs.getOrDefault(line.id(), List.of()));
			r.put("output_production_products", outputs.getOrDefault(line.id(), List.of()));
			r.put("stages", stages.getOrDefault(line.id(), List.of()));
			r.put("created_at", line.get("created_at"));
			r.put("updated_at", line.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	Map<String, Object> line(Row line, LineLoad load) {
		return lines(List.of(line), load).getFirst();
	}

	/** ProductionLine(Input|Output)ProductionProductResource */
	private Map<Long, List<Map<String, Object>>> lineProducts(String table, List<Long> lineIds, LineLoad load) {
		List<Row> rows = db.whereIn(table, "production_line_id", lineIds, "sort_order asc");
		Map<Long, Row> products = byId(db.whereIn("production_products", "id", column(rows, "production_product_id"), null));
		Map<Long, Row> types = load.lineProductTypes()
			? byId(db.whereIn("production_product_types", "id", column(rows, "production_product_type_id"), null)) : Map.of();
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row row : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", row.get("id"));
			r.put("production_line_id", row.get("production_line_id"));
			r.put("production_product_id", row.get("production_product_id"));
			r.put("production_product_type_id", row.get("production_product_type_id"));
			r.put("sort_order", row.get("sort_order"));
			Row product = products.get(row.longValue("production_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product == null ? null : product.get("id"));
			p.put("key", product == null ? null : product.get("key"));
			p.put("name", product == null ? null : product.get("name"));
			r.put("production_product", p);
			if (load.lineProductTypes()) {
				Row type = row.longValue("production_product_type_id") == null ? null : types.get(row.longValue("production_product_type_id"));
				if (type == null) {
					r.put("production_product_type", null);
				}
				else {
					Map<String, Object> t = new LinkedHashMap<>();
					t.put("id", type.get("id"));
					t.put("name", type.get("name"));
					t.put("code", type.get("code"));
					t.put("type", type.get("type"));
					t.put("unit", type.get("unit"));
					r.put("production_product_type", t);
				}
			}
			out.computeIfAbsent(row.longValue("production_line_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	/**
	 * ProductionStageResource. {@code extensions}: uzantilar yuklendiyse; {@code typeFieldsLoaded}: uzanti
	 * tipinin alanlari eager mi (degilse Laravel resource'ta tip basina lazy load eder, sonuc ayni).
	 */
	List<Map<String, Object>> stages(List<Row> stages, boolean extensions, boolean typeFieldsLoaded) {
		Map<Long, Map<String, Object>> processTypes = new LinkedHashMap<>();
		List<Row> types = db.whereIn("process_types", "id",
			stages.stream().map(st -> longOrNull(st.get("process_type_id"))).toList(), null);
		Map<Long, List<Map<String, Object>>> fields = group(fields(db.whereIn("process_type_fields", "process_type_id",
			ids(types), "sort_order asc")), "process_type_id");
		for (Row type : types) {
			processTypes.put(type.id(), processType(type, fields.getOrDefault(type.id(), List.of())));
		}
		Map<Long, List<Map<String, Object>>> stageExtensions = extensions
			? group(extensions(db.whereIn("production_stage_extensions", "production_stage_id", ids(stages), "sort_order asc"),
				typeFieldsLoaded), "production_stage_id")
			: Map.of();

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row stage : stages) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", stage.get("id"));
			r.put("production_line_id", stage.get("production_line_id"));
			r.put("process_type_id", stage.get("process_type_id"));
			r.put("name", stage.get("name"));
			r.put("stage_order", integer(stage.get("stage_order")));
			r.put("is_required", bool(stage.get("is_required")));
			r.put("requires_approval", bool(stage.get("requires_approval")));
			// ic ice new ProcessTypeResource(null) -> null
			Long typeId = longOrNull(stage.get("process_type_id"));
			r.put("process_type", typeId == null ? null : processTypes.get(typeId));
			if (extensions) {
				r.put("extensions", stageExtensions.getOrDefault(stage.id(), List.of()));
			}
			r.put("created_at", stage.get("created_at"));
			r.put("updated_at", stage.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** ProductionStageExtensionResource */
	private List<Map<String, Object>> extensions(List<Row> extensions, boolean typeFieldsLoaded) {
		List<Long> ids = ids(extensions);
		Map<Long, Row> types = byId(db.whereIn("stage_extension_types", "id", column(extensions, "stage_extension_type_id"), null));
		Map<Long, List<Map<String, Object>>> typeFields = group(extensionTypeFields(db.whereIn("stage_extension_type_fields",
			"stage_extension_type_id", new ArrayList<>(types.keySet()), "sort_order asc")), "stage_extension_type_id");

		List<Row> valueRows = db.whereIn("production_stage_extension_values", "production_stage_extension_id", ids, null);
		Map<Long, Row> valueFields = byId(db.whereIn("stage_extension_type_fields", "id", column(valueRows, "stage_extension_type_field_id"), null));
		Map<Long, List<Map<String, Object>>> values = new LinkedHashMap<>();
		for (Row v : valueRows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", v.get("id"));
			r.put("production_stage_extension_id", v.get("production_stage_extension_id"));
			r.put("stage_extension_type_field_id", v.get("stage_extension_type_field_id"));
			r.put("value_boolean", v.get("value_boolean"));
			r.put("value_integer", v.get("value_integer"));
			r.put("value_decimal", decimal4(v.decimal("value_decimal")));
			r.put("value_text", v.get("value_text"));
			r.put("value_json", v.get("value_json"));
			Row field = valueFields.get(v.longValue("stage_extension_type_field_id"));
			r.put("field", field == null ? null : extensionTypeField(field));
			values.computeIfAbsent(v.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(r);
		}
		Map<Long, List<Map<String, Object>>> inputs = extensionProducts("production_stage_extension_input_production_products", ids);
		Map<Long, List<Map<String, Object>>> outputs = extensionProducts("production_stage_extension_output_production_products", ids);

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row e : extensions) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", e.get("id"));
			r.put("production_stage_id", e.get("production_stage_id"));
			r.put("stage_extension_type_id", e.get("stage_extension_type_id"));
			r.put("is_required", e.get("is_required"));
			r.put("is_enabled", e.get("is_enabled"));
			r.put("sort_order", e.get("sort_order"));
			Row type = types.get(e.longValue("stage_extension_type_id"));
			Map<String, Object> t = new LinkedHashMap<>();
			t.put("id", type == null ? null : type.get("id"));
			t.put("key", type == null ? null : type.get("key"));
			t.put("name", type == null ? null : type.get("name"));
			t.put("description", type == null ? null : type.get("description"));
			t.put("fields", type == null ? List.of() : typeFields.getOrDefault(type.id(), List.of()));
			r.put("stage_extension_type", t);
			r.put("values", values.getOrDefault(e.id(), List.of()));
			r.put("input_production_products", inputs.getOrDefault(e.id(), List.of()));
			r.put("output_production_products", outputs.getOrDefault(e.id(), List.of()));
			out.add(r);
		}
		return out;
	}

	private Map<Long, List<Map<String, Object>>> extensionProducts(String table, List<Long> extensionIds) {
		List<Row> rows = db.whereIn(table, "production_stage_extension_id", extensionIds, "sort_order asc");
		Map<Long, Row> products = byId(db.whereIn("production_products", "id", column(rows, "production_product_id"), null));
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Row row : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", row.get("id"));
			r.put("production_stage_extension_id", row.get("production_stage_extension_id"));
			r.put("production_product_id", row.get("production_product_id"));
			r.put("sort_order", row.get("sort_order"));
			r.put("is_required", row.get("is_required"));
			Row product = products.get(row.longValue("production_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product == null ? null : product.get("id"));
			p.put("key", product == null ? null : product.get("key"));
			p.put("name", product == null ? null : product.get("name"));
			p.put("description", product == null ? null : product.get("description"));
			r.put("production_product", p);
			out.computeIfAbsent(row.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(r);
		}
		return out;
	}

	// ======================================================================
	// Proses tipleri, alanlari, uzanti tipleri
	// ======================================================================

	/** ProcessTypeResource; {@code fields} null ise yuklenmemis (anahtar yazilmaz). */
	Map<String, Object> processType(Row type, List<Map<String, Object>> fields) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", type.get("id"));
		r.put("name", type.get("name"));
		r.put("description", type.get("description"));
		r.put("category", type.get("category"));
		r.put("is_active", bool(type.get("is_active")));
		if (fields != null) {
			r.put("fields", fields);
		}
		r.put("created_at", type.get("created_at"));
		r.put("updated_at", type.get("updated_at"));
		return r;
	}

	List<Map<String, Object>> processTypes(List<Row> types) {
		Map<Long, List<Map<String, Object>>> fields = group(fields(db.whereIn("process_type_fields", "process_type_id",
			ids(types), "sort_order asc")), "process_type_id");
		return types.stream().map(t -> processType(t, fields.getOrDefault(t.id(), List.of()))).toList();
	}

	/** ProcessTypeFieldResource */
	List<Map<String, Object>> fields(List<Row> fields) {
		return fields.stream().map(this::field).toList();
	}

	Map<String, Object> field(Row f) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", f.get("id"));
		r.put("process_type_id", f.get("process_type_id"));
		r.put("field_key", f.get("field_key"));
		r.put("label", f.get("label"));
		r.put("data_type", f.get("data_type"));
		r.put("is_required", bool(f.get("is_required")));
		r.put("unit", f.get("unit"));
		r.put("sort_order", f.get("sort_order"));
		r.put("is_active", bool(f.get("is_active")));
		r.put("options", f.get("options"));
		r.put("default_value", f.get("default_value"));
		r.put("created_at", f.get("created_at"));
		r.put("updated_at", f.get("updated_at"));
		return r;
	}

	/** StageExtensionTypeResource */
	List<Map<String, Object>> extensionTypes(List<Row> types) {
		Map<Long, List<Map<String, Object>>> fields = group(extensionTypeFields(db.whereIn("stage_extension_type_fields",
			"stage_extension_type_id", ids(types), "sort_order asc, sort_order asc")), "stage_extension_type_id");
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row t : types) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", t.get("id"));
			r.put("key", t.get("key"));
			r.put("name", t.get("name"));
			r.put("description", t.get("description"));
			r.put("fields", fields.getOrDefault(t.id(), List.of()));
			r.put("created_at", t.get("created_at"));
			r.put("updated_at", t.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	private List<Map<String, Object>> extensionTypeFields(List<Row> fields) {
		return fields.stream().map(this::extensionTypeField).toList();
	}

	/** StageExtensionTypeFieldResource */
	private Map<String, Object> extensionTypeField(Row f) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", f.get("id"));
		r.put("stage_extension_type_id", f.get("stage_extension_type_id"));
		r.put("field_key", f.get("field_key"));
		r.put("label", f.get("label"));
		r.put("data_type", f.get("data_type"));
		r.put("is_required", Boolean.TRUE.equals(f.get("is_required")));
		r.put("sort_order", f.intValue("sort_order") == null ? 0 : f.intValue("sort_order"));
		r.put("default_value_boolean", f.get("default_value_boolean"));
		r.put("default_value_integer", f.get("default_value_integer"));
		r.put("default_value_decimal", decimal4(f.decimal("default_value_decimal")));
		r.put("default_value_text", f.get("default_value_text"));
		r.put("default_value_json", f.get("default_value_json"));
		r.put("options_json", f.get("options_json"));
		r.put("description", f.get("description"));
		return r;
	}

	// ======================================================================
	// Urun kategorileri, tipleri, attribute'lar
	// ======================================================================

	/** ProductionProductTypeResource (productionProduct her baglamda yuklu). */
	List<Map<String, Object>> productTypes(List<Row> types) {
		Map<Long, Row> products = byId(db.whereIn("production_products", "id",
			types.stream().map(t -> longOrNull(t.get("production_product_id"))).toList(), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row t : types) {
			Row product = products.get(longOrNull(t.get("production_product_id")));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", t.get("id"));
			r.put("name", t.get("name"));
			r.put("code", t.get("code"));
			r.put("type", t.get("type"));
			r.put("unit", t.get("unit"));
			r.put("density_kg_per_litre", decimal4(t.get("density_kg_per_litre")));
			r.put("effective_density_kg_per_litre", effectiveDensity(t, product));
			r.put("default_density_kg_per_litre", product == null ? null : decimal4(product.get("default_density_kg_per_litre")));
			r.put("production_product_id", t.get("production_product_id"));
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("id", product == null ? null : product.get("id"));
			p.put("key", product == null ? null : product.get("key"));
			p.put("name", product == null ? null : product.get("name"));
			p.put("description", product == null ? null : product.get("description"));
			r.put("production_product", p);
			r.put("created_at", t.get("created_at"));
			r.put("updated_at", t.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** ProductionProductType::effectiveDensity(): kendi > 0 ise o, yoksa kategorinin varsayilani > 0 ise o. */
	private static Double effectiveDensity(Row type, Row product) {
		BigDecimal own = toDecimal(type.get("density_kg_per_litre"));
		if (own != null && own.signum() > 0) {
			return own.doubleValue();
		}
		BigDecimal fallback = product == null ? null : toDecimal(product.get("default_density_kg_per_litre"));
		return fallback != null && fallback.signum() > 0 ? fallback.doubleValue() : null;
	}

	/**
	 * ProductionProductResource.
	 * @param count    {@code withCount}: null ise anahtar yok
	 * @param mappings attribute eslemeleri yuklendiyse (anahtar yazilir)
	 * @param types    urun tipleri yuklendiyse
	 * @param lines    girdi + cikti hatlari (tekrarsiz); her baglamda yazilir, yuklenmediyse bos
	 */
	Map<String, Object> product(Row p, Long count, List<Map<String, Object>> mappings, List<Map<String, Object>> types,
		List<Map<String, Object>> lines) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", p.get("id"));
		r.put("key", p.get("key"));
		r.put("name", p.get("name"));
		r.put("description", p.get("description"));
		r.put("default_density_kg_per_litre", decimal4(p.get("default_density_kg_per_litre")));
		if (count != null) {
			r.put("products_count", count);
		}
		if (mappings != null) {
			r.put("attribute_definitions", mappings);
		}
		if (types != null) {
			r.put("production_product_types", types);
		}
		r.put("production_lines", lines);
		return r;
	}

	/**
	 * ProductionProductAttributeDefinitionResource. {@code withDefinition}: attributeDefinition eager
	 * yuklu mu ("definition" anahtari); degilse alanlar yine accessor ile (lazy) gelir.
	 */
	List<Map<String, Object>> mappings(List<Row> mappings, boolean withDefinition) {
		Map<Long, Row> definitions = byId(db.whereIn("attribute_definitions", "id", column(mappings, "attribute_definition_id"), null));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row m : mappings) {
			Row d = definitions.get(m.longValue("attribute_definition_id"));
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", m.get("id"));
			r.put("production_product_id", m.get("production_product_id"));
			r.put("is_required", Boolean.TRUE.equals(m.get("is_required")));
			r.put("sort_order", m.intValue("sort_order") == null ? 0 : m.intValue("sort_order"));
			if (withDefinition) {
				Map<String, Object> def = new LinkedHashMap<>();
				def.put("id", d == null ? null : d.get("id"));
				def.put("attribute_key", d == null ? null : d.get("attribute_key"));
				def.put("label", d == null ? null : d.get("label"));
				def.put("data_type", d == null ? null : d.get("data_type"));
				def.put("unit", d == null ? null : d.get("unit"));
				def.put("description", d == null ? null : d.get("description"));
				r.put("definition", def);
			}
			r.put("attribute_key", d == null ? null : d.get("attribute_key"));
			r.put("label", d == null ? null : d.get("label"));
			r.put("data_type", d == null ? null : d.get("data_type"));
			r.put("unit", d == null ? null : d.get("unit"));
			r.put("created_at", m.get("created_at"));
			r.put("updated_at", m.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** AttributeDefinitionResource */
	Map<String, Object> attributeDefinition(Row d) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", d.get("id"));
		r.put("attribute_key", d.get("attribute_key"));
		r.put("label", d.get("label"));
		r.put("data_type", d.get("data_type"));
		r.put("unit", d.get("unit"));
		r.put("description", d.get("description"));
		r.put("created_at", d.get("created_at"));
		r.put("updated_at", d.get("updated_at"));
		return r;
	}

	/**
	 * Bir urun kategorisinin girdi ve cikti hatlari ({@code belongsToMany}, siralama yok), girdiler once,
	 * tekrarsiz. Her grubun iliskileri Laravel'deki gibi kendi icinde yuklenir.
	 */
	List<Map<String, Object>> productLines(long productId) {
		List<Row> inputs = db.select("""
			select production_lines.* from production_lines
			inner join production_line_input_production_products
			  on production_lines.id = production_line_input_production_products.production_line_id
			where production_line_input_production_products.production_product_id in (%d)""".formatted(productId));
		List<Row> outputs = db.select("""
			select production_lines.* from production_lines
			inner join production_line_output_production_products
			  on production_lines.id = production_line_output_production_products.production_line_id
			where production_line_output_production_products.production_product_id in (%d)""".formatted(productId));
		return mergeLines(lines(inputs, LineLoad.BASIC), lines(outputs, LineLoad.BASIC));
	}

	static List<Map<String, Object>> mergeLines(List<Map<String, Object>> first, List<Map<String, Object>> second) {
		Map<Object, Map<String, Object>> unique = new LinkedHashMap<>();
		for (Map<String, Object> line : first) {
			unique.putIfAbsent(line.get("id"), line);
		}
		for (Map<String, Object> line : second) {
			unique.putIfAbsent(line.get("id"), line);
		}
		return new ArrayList<>(unique.values());
	}

	// ======================================================================
	// yardimcilar
	// ======================================================================

	/** Laravel {@code decimal:4} cast'i: 4 basamakli metin ("0.9150"). */
	static String decimal4(Object value) {
		BigDecimal d = toDecimal(value);
		return d == null ? null : d.setScale(4, RoundingMode.HALF_UP).toPlainString();
	}

	/** Deger istekten de gelebilir (sayi ya da metin); sayi olmayan metin Laravel'de de hata (brick/math). */
	private static BigDecimal toDecimal(Object value) {
		return switch (value) {
			case null -> null;
			case BigDecimal d -> d;
			case Number n -> new BigDecimal(n.toString());
			case Boolean b -> b ? BigDecimal.ONE : BigDecimal.ZERO;
			default -> new BigDecimal(value.toString().strip());
		};
	}

	/** Laravel {@code boolean} cast'i: null kalir, digerleri PHP (bool). */
	static Object bool(Object value) {
		return switch (value) {
			case null -> null;
			case Boolean b -> b;
			case String s -> !s.isEmpty() && !s.equals("0");
			case Number n -> new BigDecimal(n.toString()).signum() != 0;
			default -> true;
		};
	}

	/** Laravel {@code integer} cast'i: null kalir, digerleri (int). */
	static Object integer(Object value) {
		return value == null ? null : com.olivamore.api.shared.php.Php.intval(value);
	}

	static Long longOrNull(Object value) {
		return value == null ? null : com.olivamore.api.shared.php.Php.intval(value);
	}

	static List<Long> ids(Collection<Row> rows) {
		return rows.stream().map(Row::id).toList();
	}

	static List<Long> column(Collection<Row> rows, String column) {
		Set<Long> out = new LinkedHashSet<>();
		for (Row row : rows) {
			Long v = row.longValue(column);
			if (v != null) {
				out.add(v);
			}
		}
		return new ArrayList<>(out);
	}

	static Map<Long, Row> byId(List<Row> rows) {
		return rows.stream().collect(Collectors.toMap(Row::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
	}

	static Map<Long, List<Map<String, Object>>> group(List<Map<String, Object>> items, String key) {
		Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Map<String, Object> item : items) {
			out.computeIfAbsent(((Number) item.get(key)).longValue(), k -> new ArrayList<>()).add(item);
		}
		return out;
	}

}
