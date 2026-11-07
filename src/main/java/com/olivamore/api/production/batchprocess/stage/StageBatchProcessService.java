package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.olivamore.api.production.batchprocess.stage.StageExtensions.Extension;

/**
 * Asama isleyicilerinin ortak tabani: veritabani, stok hareketleri ve Laravel servislerinin paylastigi yardimcilar
 * (aday urun tipleri ve materyaller, materyal yaratma, findOrFail, PHP truthiness).
 */
abstract class StageBatchProcessService {

	/** materials.quantity decimal(14,4) */
	static final double QUANTITY_EPSILON = 0.00005;

	protected final Db db;
	protected final StockOperations stock;

	StageBatchProcessService(Db db, StockOperations stock) {
		this.db = db;
		this.stock = stock;
	}

	/** Partinin giris + cikis urun tipleri (sort_order), uzantinin girdi kategorilerinden olanlar, tekil. */
	List<Row> candidateTypes(Row process, Extension extension) {
		List<Row> all = new ArrayList<>(batchTypes("production_batch_input_production_product_types", process));
		all.addAll(batchTypes("production_batch_output_production_product_types", process));
		return unique(all.stream().filter(t -> extension.inputProductIds().contains(Php.intval(t.get("production_product_id")))).toList());
	}

	List<Row> batchTypes(String table, Row process) {
		List<Row> links = db.whereIn(table, "production_batch_id", List.of(Php.intval(process.get("production_batch_id"))), "sort_order asc");
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(links, "production_product_type_id"), null));
		List<Row> out = new ArrayList<>();
		for (Row link : links) {
			Row type = types.get(Relations.key(link.get("production_product_type_id")));
			if (type != null) {
				out.add(type);
			}
		}
		return out;
	}

	/** Partinin aktif materyalleri, tipi verilen kategorilerden (kategori yoksa hic). */
	List<Row> candidateMaterials(Row process, List<Long> productIds) {
		if (productIds.isEmpty()) {
			return List.of();
		}
		return db.select("select * from materials where production_batch_id = ? and status = ? and exists (select * from"
			+ " production_product_types where materials.production_product_type_id = production_product_types.id and"
			+ " production_product_id in (" + Relations.inList(productIds) + "))", process.get("production_batch_id"), "active");
	}

	long createMaterial(Row process, long typeId, Long parentId, double quantity, Object unit) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("production_batch_id", process.get("production_batch_id"));
		values.put("production_product_type_id", typeId);
		values.put("parent_material_id", parentId);
		values.put("origin_type", "BATCH_PROCESS");
		values.put("quantity", quantity);
		values.put("unit", unit);
		values.put("status", "active");
		values.put("current_location_type", "production_batch");
		values.put("current_location_id", process.get("production_batch_id"));
		values.put("source_production_stage_id", process.get("production_stage_id"));
		values.put("created_by_batch_process_id", process.id());
		values.put("closed_by_batch_process_id", null);
		values.put("note", process.get("note"));
		return db.insert("materials", values);
	}

	Row findOrFail(String table, String model, Object id) {
		return db.first("select * from " + table + " where " + table + ".id = ? limit 1", id)
			.orElseThrow(() -> new ModelNotFoundException(model, id));
	}

	static Extension first(List<Extension> extensions, String key) {
		return extensions.stream().filter(e -> e.enabled() && key.equals(e.key())).findFirst().orElse(null);
	}

	static Extension require(List<Extension> extensions, String key, String message) {
		Extension extension = first(extensions, key);
		if (extension == null) {
			throw new BusinessRuleException(message);
		}
		return extension;
	}

	static List<Row> unique(List<Row> rows) {
		List<Row> out = new ArrayList<>();
		for (Row r : rows) {
			if (out.stream().noneMatch(o -> o.id() == r.id())) {
				out.add(r);
			}
		}
		return out;
	}

	/** {@code $data['x'] ?? []} uzerinde foreach: her eleman bir dizi (degilse bos). */
	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> items(Object value) {
		List<Object> list = value instanceof List<?> l ? new ArrayList<>(l)
			: value instanceof Map<?, ?> m ? new ArrayList<>(m.values()) : List.of();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Object item : list) {
			out.add(StageExtensions.asMap(item));
		}
		return out;
	}

	/** decimal:4 cast'inin yazdigi deger (PHP float metni, 4 basamak). */
	static String decimal4(double value) {
		return Casts.decimal(Php.floatString(value), 4);
	}

	/** PHP {@code ==} metin/null icin: null ile "" esit. */
	static boolean looseEquals(Object a, Object b) {
		Object x = a == null ? "" : a;
		Object y = b == null ? "" : b;
		return Php.strval(x).equals(Php.strval(y));
	}

	/** PHP {@code !empty()} */
	static boolean truthy(Object value) {
		return switch (value) {
			case null -> false;
			case Boolean b -> b;
			case String s -> !s.isEmpty() && !s.equals("0");
			case Number n -> new java.math.BigDecimal(n.toString()).signum() != 0;
			case java.util.Collection<?> c -> !c.isEmpty();
			case Map<?, ?> m -> !m.isEmpty();
			default -> true;
		};
	}

}
