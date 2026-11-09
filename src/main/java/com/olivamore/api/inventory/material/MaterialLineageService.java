package com.olivamore.api.inventory.material;

import com.olivamore.api.inventory.material.MaterialLineageResponse.Edge;
import com.olivamore.api.inventory.material.MaterialLineageResponse.MaterialNode;
import com.olivamore.api.inventory.material.MaterialLineageResponse.Node;
import com.olivamore.api.inventory.material.MaterialLineageResponse.OtherNode;
import com.olivamore.api.inventory.material.MaterialQueries.Batch;
import com.olivamore.api.inventory.material.MaterialQueries.MaterialRow;
import com.olivamore.api.inventory.material.MaterialQueries.ProductType;
import com.olivamore.api.inventory.material.MaterialQueries.Unit;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.olivamore.api.inventory.material.MaterialResponses.lookup;

/**
 * Materyalin soy grafi (DAG): koke cikilir, kokten butun torunlara inilir (en fazla 400 dugum), sonra dolum
 * kosulari, depo yerlestirmeleri ve (tek kosudan beslenen depolarda) siparisler eklenir. Dugumlere kokten en
 * uzun yol derinligi verilir.
 */
@Service
class MaterialLineageService {

	static final int MAX_NODES = 400;

	private record StageRow(Long id, String name) {
	}

	private record RunMaterial(Long finishedProductRunId, Long sourceMaterialId, BigDecimal quantityUsed) {
	}

	private record Run(Long id, Long finishedProductId, Integer quantity, String status) {
	}

	private record StockMove(Long finishedProductRunId, Long finishedProductStorageId,
		Long finishedProductStorageSlotId,
		Long finishedProductId, long quantity, Long orderId) {
	}

	private record OrderRow(Long id, Long customerId, String status) {
	}

	private final MaterialQueries queries;
	private final JdbcClient jdbc;

	MaterialLineageService(MaterialQueries queries, JdbcClient jdbc) {
		this.queries = queries;
		this.jdbc = jdbc;
	}

	MaterialLineageResponse build(long id) {
		MaterialRow material = queries.material(id).orElseThrow(() -> new ModelNotFoundException("Material", id));
		List<MaterialRow> materials = collect(material);
		boolean truncated = materials.size() >= MAX_NODES;

		Map<Long, ProductType> types = queries.productTypes(column(materials, MaterialRow::productionProductTypeId));
		Map<Long, Batch> batches = queries.batches(column(materials, MaterialRow::productionBatchId));
		Map<Long, StageRow> stages = idRows("production_stages", column(materials, MaterialRow::sourceProductionStageId),
			StageRow.class, StageRow::id);
		Map<Long, Unit> units = queries.units(column(materials, MaterialRow::currentLocationId));

		Map<String, Node> nodes = new LinkedHashMap<>();
		List<Edge> edges = new ArrayList<>();
		for (MaterialRow m : materials) {
			nodes.put("material:" + m.id(), materialNode(m, m.id().equals(material.id()), types, batches, stages, units));
		}
		for (MaterialRow m : materials) {
			if (m.parentMaterialId() != null && nodes.containsKey("material:" + m.parentMaterialId())) {
				StageRow stage = lookup(stages, m.sourceProductionStageId());
				edges.add(new Edge("material:" + m.parentMaterialId(), "material:" + m.id(),
					stage == null ? null : stage.name(), Quantities.number(m.quantity()), m.unit()));
			}
		}
		appendRuns(materials, nodes, edges);
		return new MaterialLineageResponse("material:" + material.id(), withDepths(nodes, edges), edges, truncated);
	}

	/** Koke cik (dongu korumali), kokten genislik oncelikli in; odak her zaman listede. id sirasinda. */
	private List<MaterialRow> collect(MaterialRow material) {
		Map<Long, MaterialRow> found = new LinkedHashMap<>();
		MaterialRow root = material;
		int guard = 0;
		while (root.parentMaterialId() != null && guard++ < MAX_NODES) {
			MaterialRow parent = queries.material(root.parentMaterialId()).orElse(null);
			if (parent == null || found.containsKey(parent.id())) {
				break;
			}
			found.put(parent.id(), parent);
			root = parent;
		}
		found.put(root.id(), root);
		List<Long> frontier = List.of(root.id());
		while (!frontier.isEmpty() && found.size() < MAX_NODES) {
			List<Long> next = new ArrayList<>();
			for (MaterialRow child : queries.children(frontier)) {
				if (found.putIfAbsent(child.id(), child) == null) {
					next.add(child.id());
				}
			}
			frontier = next;
		}
		found.put(material.id(), material);
		return found.values().stream().sorted(Comparator.comparingLong(MaterialRow::id)).toList();
	}

	/** Dolum kosusu dugumleri (kosu basina bir) ve materyal -> kosu kenarlari; sonra depo yerlestirmeleri. */
	private void appendRuns(List<MaterialRow> materials, Map<String, Node> nodes, List<Edge> edges) {
		List<RunMaterial> runMaterials = jdbc.sql("""
			select finished_product_run_id, source_material_id, quantity_used from finished_product_run_materials
			where source_material_id in (:ids)
			""").param("ids", materials.stream().map(MaterialRow::id).toList()).query(RunMaterial.class).list();
		if (runMaterials.isEmpty()) {
			return;
		}
		Map<Long, Run> runs = idRows("finished_product_runs", column(runMaterials, RunMaterial::finishedProductRunId),
			Run.class, Run::id);
		Map<Long, String> products = names("finished_products", column(runs.values(), Run::finishedProductId));
		Map<Long, MaterialRow> materialById = materials.stream()
			.collect(Collectors.toMap(MaterialRow::id, Function.identity()));
		for (RunMaterial rm : runMaterials) {
			Run run = lookup(runs, rm.finishedProductRunId());
			if (run == null) {
				continue;
			}
			String runKey = "run:" + run.id();
			if (!nodes.containsKey(runKey)) {
				String product = lookup(products, run.finishedProductId());
				nodes.put(runKey, new OtherNode(runKey, "finished_product_run", run.id(),
					product != null ? product : "Dolum #" + run.id(), run.quantity() + " adet", run.status(), false, 0));
			}
			MaterialRow source = lookup(materialById, rm.sourceMaterialId());
			edges.add(new Edge("material:" + rm.sourceMaterialId(), runKey, "Dolum", Quantities.number(rm.quantityUsed()),
				source == null ? null : source.unit()));
		}
		appendPlacements(new ArrayList<>(nodes.keySet()), nodes, edges);
	}

	/** Kosunun ciktisinin depo basina toplami (raf adlari alt satirda). */
	private void appendPlacements(List<String> keys, Map<String, Node> nodes, List<Edge> edges) {
		List<Long> runIds = keys.stream().filter(k -> k.startsWith("run:"))
			.map(k -> Long.parseLong(k.substring(4))).toList();
		if (runIds.isEmpty()) {
			return;
		}
		List<StockMove> movements = jdbc.sql("""
			select finished_product_run_id, finished_product_storage_id, finished_product_storage_slot_id,
				finished_product_id, quantity, order_id
			from finished_product_stock_movements where finished_product_run_id in (:ids) and movement_type = 'stock_in'
			""").param("ids", runIds).query(StockMove.class).list();
		Map<Long, String> storages = names("finished_product_storages",
			column(movements, StockMove::finishedProductStorageId));
		Map<Long, String> slots = names("finished_product_storage_slots",
			column(movements, StockMove::finishedProductStorageSlotId));
		Map<String, List<StockMove>> groups = new LinkedHashMap<>();
		for (StockMove m : movements) {
			groups.computeIfAbsent(m.finishedProductRunId() + ":" + m.finishedProductStorageId(),
				k -> new ArrayList<>()).add(m);
		}
		for (List<StockMove> group : groups.values()) {
			StockMove first = group.getFirst();
			String key = "placement:" + first.finishedProductRunId() + ":" + first.finishedProductStorageId();
			long total = 0;
			Set<String> slotNames = new LinkedHashSet<>();
			for (StockMove m : group) {
				total += m.quantity();
				String name = lookup(slots, m.finishedProductStorageSlotId());
				// bos ve "0" adlar atilir
				if (name != null && !name.isEmpty() && !name.equals("0")) {
					slotNames.add(name);
				}
			}
			String names = String.join(", ", slotNames);
			String storage = lookup(storages, first.finishedProductStorageId());
			nodes.put(key, new OtherNode(key, "storage_placement", first.finishedProductStorageId(),
				storage != null ? storage : "Depo", names.isEmpty() ? total + " adet" : total + " adet · " + names, null,
				false, 0));
			edges.add(new Edge("run:" + first.finishedProductRunId(), key, "Rafa kondu", total, "ADET"));
			appendOrders(first, key, nodes, edges);
		}
	}

	/** Depo bu urun icin tek bir kosudan beslendiyse, oradan cikilan siparisler. */
	private void appendOrders(StockMove placement, String placementKey, Map<String, Node> nodes, List<Edge> edges) {
		long storageId = placement.finishedProductStorageId();
		long productId = placement.finishedProductId();
		List<Long> feeding = jdbc.sql("""
			select distinct finished_product_run_id from finished_product_stock_movements
			where finished_product_storage_id = :storage and finished_product_id = :product and movement_type = 'stock_in'
			""").param("storage", storageId).param("product", productId).query(Long.class).list();
		if (feeding.size() != 1 || feeding.getFirst() == null) {
			return;
		}
		List<StockMove> picks = jdbc.sql("""
			select finished_product_run_id, finished_product_storage_id, finished_product_storage_slot_id,
				finished_product_id, quantity, order_id
			from finished_product_stock_movements
			where finished_product_storage_id = :storage and finished_product_id = :product
				and movement_type = 'order_pick' and order_id is not null
			""").param("storage", storageId).param("product", productId).query(StockMove.class).list();
		Map<Long, OrderRow> orders = idRows("orders", column(picks, StockMove::orderId), OrderRow.class, OrderRow::id);
		Map<Long, String> customers = names("customers", column(orders.values(), OrderRow::customerId));
		Map<Long, List<StockMove>> groups = new LinkedHashMap<>();
		for (StockMove pick : picks) {
			groups.computeIfAbsent(pick.orderId(), k -> new ArrayList<>()).add(pick);
		}
		groups.forEach((orderId, group) -> {
			OrderRow order = orders.get(orderId);
			if (order == null) {
				return;
			}
			String key = "order:" + orderId;
			long total = group.stream().mapToLong(StockMove::quantity).sum();
			if (!nodes.containsKey(key)) {
				String customer = lookup(customers, order.customerId());
				nodes.put(key, new OtherNode(key, "order", orderId, customer != null ? customer : "Sipariş #" + orderId,
					total + " adet", order.status(), false, 0));
			}
			edges.add(new Edge(placementKey, key, "Siparişe çıktı", total, "ADET"));
		});
	}

	private static MaterialNode materialNode(MaterialRow m, boolean focus, Map<Long, ProductType> types,
		Map<Long, Batch> batches, Map<Long, StageRow> stages, Map<Long, Unit> units) {
		ProductType type = lookup(types, m.productionProductTypeId());
		Batch batch = lookup(batches, m.productionBatchId());
		StageRow stage = lookup(stages, m.sourceProductionStageId());
		Unit unit = lookup(units, m.currentLocationId());
		return new MaterialNode("material:" + m.id(), "material", m.id(),
			type != null && type.name() != null ? type.name() : "Materyal", formatQuantity(m),
			Quantities.number(m.quantity()), m.unit(), m.status(), MaterialService.lineageRole(m), m.originType(),
			batch == null ? null : batch.batchCode(), stage == null ? null : stage.name(),
			"storage_unit".equals(m.currentLocationType()) && unit != null ? unit.code() : null, focus, 0);
	}

	/** "1.234 L", "12,5 kg": tam sayiysa ondaliksiz, degilse 2 basamak ve sondaki sifirlar atilir. */
	static String formatQuantity(MaterialRow m) {
		BigDecimal q = m.quantity();
		boolean whole = q.stripTrailingZeros().scale() <= 0;
		String text = whole ? grouped(q.setScale(0, RoundingMode.HALF_UP).toPlainString(), "")
			: stripped(q.setScale(2, RoundingMode.HALF_UP).toPlainString());
		String unit = String.valueOf(m.unit());
		return text + " " + switch (unit) {
			case "LITRE" -> "L";
			case "KG" -> "kg";
			default -> unit;
		};
	}

	/** 2 basamakli sabit gosterimden: binlik nokta, ondalik virgul; sondaki sifirlar ve virgul atilir. */
	private static String stripped(String plain) {
		int dot = plain.indexOf('.');
		String text = grouped(plain.substring(0, dot), plain.substring(dot + 1));
		return text.replaceAll("0+$", "").replaceAll(",+$", "");
	}

	private static String grouped(String digits, String fraction) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (digits.length() - i) % 3 == 0) {
				out.append('.');
			}
			out.append(digits.charAt(i));
		}
		return fraction.isEmpty() ? out.toString() : out + "," + fraction;
	}

	/** Kenarlar uzerinden en uzun yol; (derinlik, anahtar) sirasi. */
	private static List<Node> withDepths(Map<String, Node> nodes, List<Edge> edges) {
		Map<String, Integer> depths = new LinkedHashMap<>();
		nodes.keySet().forEach(k -> depths.put(k, 0));
		int rounds = 0;
		boolean changed = true;
		while (changed && rounds++ < nodes.size() + 1) {
			changed = false;
			for (Edge e : edges) {
				Integer from = depths.get(e.from());
				Integer to = depths.get(e.to());
				if (from == null || to == null) {
					continue;
				}
				if (from + 1 > to) {
					depths.put(e.to(), from + 1);
					changed = true;
				}
			}
		}
		List<Node> result = new ArrayList<>();
		nodes.forEach((key, node) -> result.add(node.withDepth(depths.getOrDefault(key, 0))));
		result.sort(Comparator.comparingInt(Node::depth).thenComparing(Node::key));
		return result;
	}

	private <T> Map<Long, T> idRows(String table, Collection<Long> ids, Class<T> type, Function<T, Long> key) {
		List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select * from " + table + " where id in (:ids)").param("ids", present)
			.query(type).list().stream()
			.collect(Collectors.toMap(key, Function.identity()));
	}

	private Map<Long, String> names(String table, Collection<Long> ids) {
		record NameRow(Long id, String name) {
		}
		return idRows(table, ids, NameRow.class, NameRow::id).values().stream()
			.filter(row -> row.name() != null)
			.collect(Collectors.toMap(NameRow::id, NameRow::name));
	}

	private static <S> List<Long> column(Collection<S> rows, Function<S, Long> column) {
		return rows.stream().map(column).filter(Objects::nonNull).toList();
	}

}
