package com.olivamore.api.inventory;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code MaterialLineageService::build}: materyalin soy grafi (DAG). Koke cikilir, kokten butun
 * torunlara inilir (en fazla 400 dugum), sonra dolum kosulari, depo yerlestirmeleri ve (tek kosudan
 * beslenen depolarda) siparisler eklenir. Dugumlere kokten en uzun yol derinligi verilir.
 */
@Component
class MaterialLineage {

	static final int MAX_NODES = 400;

	private final Db db;

	MaterialLineage(Db db) {
		this.db = db;
	}

	Map<String, Object> build(Row material) {
		List<Row> materials = collect(material);
		boolean truncated = materials.size() >= MAX_NODES;

		Map<Long, Row> types = byId("production_product_types", materials, "production_product_type_id");
		Map<Long, Row> batches = byId("production_batches", materials, "production_batch_id");
		Map<Long, Row> stages = byId("production_stages", materials, "source_production_stage_id");
		Map<Long, Row> units = byId("storage_units", materials, "current_location_id");

		Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
		List<Map<String, Object>> edges = new ArrayList<>();
		for (Row m : materials) {
			nodes.put("material:" + m.id(), materialNode(m, m.id() == material.id(), types, batches, stages, units));
		}
		for (Row m : materials) {
			Long parent = Relations.key(m.get("parent_material_id"));
			if (parent != null && nodes.containsKey("material:" + parent)) {
				Row stage = stages.get(Relations.key(m.get("source_production_stage_id")));
				edges.add(edge("material:" + parent, "material:" + m.id(), stage == null ? null : stage.get("name"),
					Php.jsonFloat(quantity(m)), m.get("unit")));
			}
		}
		appendRuns(materials, nodes, edges);

		Map<String, Object> r = new LinkedHashMap<>();
		r.put("focus", "material:" + material.id());
		r.put("nodes", withDepths(nodes, edges));
		r.put("edges", edges);
		r.put("truncated", truncated);
		return r;
	}

	/** Koke cik (dongu korumali), kokten genislik oncelikli in; odak her zaman listede. id sirasinda. */
	private List<Row> collect(Row material) {
		Map<Long, Row> found = new LinkedHashMap<>();
		Row root = material;
		int guard = 0;
		while (root.get("parent_material_id") != null && guard++ < MAX_NODES) {
			Row parent = db.find("materials", Relations.key(root.get("parent_material_id"))).orElse(null);
			if (parent == null || found.containsKey(parent.id())) {
				break;
			}
			found.put(parent.id(), parent);
			root = parent;
		}
		found.put(root.id(), root);
		List<Long> frontier = List.of(root.id());
		while (!frontier.isEmpty() && found.size() < MAX_NODES) {
			List<Row> children = db.select("select * from materials where parent_material_id in ("
				+ Relations.inList(frontier) + ") order by id asc");
			List<Long> next = new ArrayList<>();
			for (Row child : children) {
				if (found.containsKey(child.id())) {
					continue;
				}
				found.put(child.id(), child);
				next.add(child.id());
			}
			frontier = next;
		}
		found.put(material.id(), material);
		return found.values().stream().sorted(Comparator.comparingLong(Row::id)).toList();
	}

	/** Dolum kosusu dugumleri (kosu basina bir) ve materyal -> kosu kenarlari; sonra depo yerlestirmeleri. */
	private void appendRuns(List<Row> materials, Map<String, Map<String, Object>> nodes, List<Map<String, Object>> edges) {
		List<Row> runMaterials = db.select("select * from finished_product_run_materials where source_material_id in ("
			+ Relations.inList(Relations.ids(materials)) + ")");
		if (runMaterials.isEmpty()) {
			return;
		}
		Map<Long, Row> runs = byId("finished_product_runs", runMaterials, "finished_product_run_id");
		Map<Long, Row> products = byId("finished_products", runs.values(), "finished_product_id");
		Map<Long, Row> materialById = Relations.byId(materials);
		for (Row rm : runMaterials) {
			Row run = runs.get(Relations.key(rm.get("finished_product_run_id")));
			if (run == null) {
				continue;
			}
			String runKey = "run:" + run.id();
			if (!nodes.containsKey(runKey)) {
				Row product = products.get(Relations.key(run.get("finished_product_id")));
				nodes.put(runKey, node(runKey, "finished_product_run", run.get("id"),
					product != null && product.get("name") != null ? product.get("name") : "Dolum #" + run.id(),
					Php.strval(run.get("quantity")) + " adet", run.get("status")));
			}
			Row source = materialById.get(Relations.key(rm.get("source_material_id")));
			edges.add(edge("material:" + Php.strval(rm.get("source_material_id")), runKey, "Dolum",
				Php.jsonFloat(Php.floatval(Casts.decimal(rm.get("quantity_used"), 4))), source == null ? null : source.get("unit")));
		}
		appendPlacements(new ArrayList<>(nodes.keySet()), nodes, edges);
	}

	/** Kosunun ciktisinin depo basina toplami (raf adlari alt satirda). */
	private void appendPlacements(List<String> keys, Map<String, Map<String, Object>> nodes, List<Map<String, Object>> edges) {
		List<Long> runIds = keys.stream().filter(k -> k.startsWith("run:")).map(k -> Php.intval(k.substring(4))).toList();
		if (runIds.isEmpty()) {
			return;
		}
		List<Row> movements = db.select("select * from finished_product_stock_movements where finished_product_run_id in ("
			+ Relations.inList(runIds) + ") and movement_type = ?", "stock_in");
		Map<Long, Row> storages = byId("finished_product_storages", movements, "finished_product_storage_id");
		Map<Long, Row> slots = byId("finished_product_storage_slots", movements, "finished_product_storage_slot_id");
		Map<String, List<Row>> groups = new LinkedHashMap<>();
		for (Row m : movements) {
			groups.computeIfAbsent(Php.strval(m.get("finished_product_run_id")) + ":" + Php.strval(m.get("finished_product_storage_id")),
				k -> new ArrayList<>()).add(m);
		}
		for (List<Row> group : groups.values()) {
			Row first = group.getFirst();
			String key = "placement:" + Php.strval(first.get("finished_product_run_id")) + ":" + Php.strval(first.get("finished_product_storage_id"));
			long total = 0;
			Set<String> slotNames = new LinkedHashSet<>();
			for (Row m : group) {
				total += Php.intval(m.get("quantity"));
				Row slot = slots.get(Relations.key(m.get("finished_product_storage_slot_id")));
				Object name = slot == null ? null : slot.get("name");
				// collect()->filter(): null, "" ve "0" atilir
				if (name != null && !Php.strval(name).isEmpty() && !Php.strval(name).equals("0")) {
					slotNames.add(Php.strval(name));
				}
			}
			String names = String.join(", ", slotNames);
			Row storage = storages.get(Relations.key(first.get("finished_product_storage_id")));
			nodes.put(key, node(key, "storage_placement", first.get("finished_product_storage_id"),
				storage != null && storage.get("name") != null ? storage.get("name") : "Depo",
				names.isEmpty() ? total + " adet" : total + " adet · " + names, null));
			edges.add(edge("run:" + Php.strval(first.get("finished_product_run_id")), key, "Rafa kondu", total, "ADET"));
			appendOrders(first, key, nodes, edges);
		}
	}

	/** Depo bu urun icin tek bir kosudan beslendiyse, oradan cikilan siparisler. */
	private void appendOrders(Row placement, String placementKey, Map<String, Map<String, Object>> nodes,
		List<Map<String, Object>> edges) {
		long storageId = Php.intval(placement.get("finished_product_storage_id"));
		long productId = Php.intval(placement.get("finished_product_id"));
		List<Row> feeding = db.select("select distinct finished_product_run_id from finished_product_stock_movements"
			+ " where finished_product_storage_id = ? and finished_product_id = ? and movement_type = ?", storageId, productId, "stock_in");
		if (feeding.size() != 1 || feeding.getFirst().get("finished_product_run_id") == null) {
			return;
		}
		List<Row> picks = db.select("select * from finished_product_stock_movements where finished_product_storage_id = ?"
			+ " and finished_product_id = ? and movement_type = ? and order_id is not null", storageId, productId, "order_pick");
		Map<Long, Row> orders = byId("orders", picks, "order_id");
		Map<Long, Row> customers = byId("customers", orders.values(), "customer_id");
		Map<Long, List<Row>> groups = Relations.groupBy(picks, "order_id");
		for (Map.Entry<Long, List<Row>> e : groups.entrySet()) {
			Row order = orders.get(e.getKey());
			if (order == null) {
				continue;
			}
			String key = "order:" + e.getKey();
			long total = e.getValue().stream().mapToLong(m -> Php.intval(m.get("quantity"))).sum();
			if (!nodes.containsKey(key)) {
				Row customer = customers.get(Relations.key(order.get("customer_id")));
				nodes.put(key, node(key, "order", e.getKey(),
					customer != null && customer.get("name") != null ? customer.get("name") : "Sipariş #" + e.getKey(),
					total + " adet", order.get("status")));
			}
			edges.add(edge(placementKey, key, "Siparişe çıktı", total, "ADET"));
		}
	}

	private static Map<String, Object> materialNode(Row m, boolean focus, Map<Long, Row> types, Map<Long, Row> batches,
		Map<Long, Row> stages, Map<Long, Row> units) {
		Row type = types.get(Relations.key(m.get("production_product_type_id")));
		Row batch = batches.get(Relations.key(m.get("production_batch_id")));
		Row stage = stages.get(Relations.key(m.get("source_production_stage_id")));
		Row unit = units.get(Relations.key(m.get("current_location_id")));
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("key", "material:" + m.id());
		r.put("kind", "material");
		r.put("id", m.get("id"));
		r.put("title", type != null && type.get("name") != null ? type.get("name") : "Materyal");
		r.put("subtitle", formatQuantity(m));
		r.put("quantity", Php.jsonFloat(quantity(m)));
		r.put("unit", m.get("unit"));
		r.put("status", m.get("status"));
		r.put("lineage_role", MaterialController.lineageRole(m));
		r.put("origin_type", m.get("origin_type"));
		r.put("batch_code", batch == null ? null : batch.get("batch_code"));
		r.put("stage_name", stage == null ? null : stage.get("name"));
		r.put("storage_unit", "storage_unit".equals(m.get("current_location_type")) && unit != null ? unit.get("code") : null);
		r.put("is_focus", focus);
		return r;
	}

	/** "1.234 L", "12,5 kg": tam sayiysa ondaliksiz, degilse 2 basamak ve sondaki sifirlar atilir. */
	static String formatQuantity(Row m) {
		double q = quantity(m);
		String text;
		if (q == Math.floor(q)) {
			text = Php.numberFormat(q, 0, ",", ".");
		}
		else {
			text = Php.numberFormat(q, 2, ",", ".").replaceAll("0+$", "").replaceAll(",+$", "");
		}
		String unit = Php.strval(m.get("unit"));
		return text + " " + switch (unit) {
			case "LITRE" -> "L";
			case "KG" -> "kg";
			default -> unit;
		};
	}

	private static double quantity(Row m) {
		return Php.floatval(Casts.decimal(m.get("quantity"), 4));
	}

	/** Kenarlar uzerinden en uzun yol; (derinlik, anahtar) sirasi. */
	private static List<Map<String, Object>> withDepths(Map<String, Map<String, Object>> nodes, List<Map<String, Object>> edges) {
		Map<String, Integer> depths = new LinkedHashMap<>();
		nodes.keySet().forEach(k -> depths.put(k, 0));
		int rounds = 0;
		boolean changed = true;
		while (changed && rounds++ < nodes.size() + 1) {
			changed = false;
			for (Map<String, Object> e : edges) {
				Integer from = depths.get((String) e.get("from"));
				Integer to = depths.get((String) e.get("to"));
				if (from == null || to == null) {
					continue;
				}
				if (from + 1 > to) {
					depths.put((String) e.get("to"), from + 1);
					changed = true;
				}
			}
		}
		List<Map<String, Object>> result = new ArrayList<>();
		nodes.forEach((key, node) -> {
			Map<String, Object> n = new LinkedHashMap<>(node);
			n.put("depth", depths.getOrDefault(key, 0));
			result.add(n);
		});
		result.sort(Comparator.comparing((Map<String, Object> n) -> (Integer) n.get("depth"))
			.thenComparing(n -> (String) n.get("key")));
		return result;
	}

	private static Map<String, Object> node(String key, String kind, Object id, Object title, String subtitle, Object status) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("key", key);
		r.put("kind", kind);
		r.put("id", id instanceof Number n ? n.longValue() : id);
		r.put("title", title);
		r.put("subtitle", subtitle);
		r.put("status", status);
		r.put("is_focus", false);
		return r;
	}

	private static Map<String, Object> edge(String from, String to, Object label, Object quantity, Object unit) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("from", from);
		r.put("to", to);
		r.put("label", label);
		r.put("quantity", quantity);
		r.put("unit", unit);
		return r;
	}

	private Map<Long, Row> byId(String table, java.util.Collection<Row> rows, String column) {
		return Relations.byId(db.whereIn(table, "id", Relations.column(rows, column), null));
	}

}
