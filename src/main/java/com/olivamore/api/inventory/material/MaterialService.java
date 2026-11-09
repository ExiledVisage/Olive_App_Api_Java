package com.olivamore.api.inventory.material;

import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialDetailResponse.LineageSummary;
import com.olivamore.api.inventory.material.MaterialDetailResponse.TypeSummary;
import com.olivamore.api.inventory.material.MaterialQueries.Batch;
import com.olivamore.api.inventory.material.MaterialQueries.MaterialRow;
import com.olivamore.api.inventory.material.MaterialQueries.Product;
import com.olivamore.api.inventory.material.MaterialQueries.ProductType;
import com.olivamore.api.inventory.material.MaterialQueries.Unit;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.olivamore.api.inventory.material.MaterialResponses.lookup;

/** Materyal listesi (durum, soy suzgeci, arama; en yeni 200) ve detayi. */
@Service
class MaterialService {

	static final int LIST_LIMIT = 200;
	static final int MOVEMENT_LIMIT = 20;

	private static final List<String> STATUSES = List.of("active", "consumed");
	private static final List<String> LINEAGES = List.of("root", "root_raw", "root_opening", "derived");

	private final MaterialQueries queries;
	private final JdbcClient jdbc;

	MaterialService(MaterialQueries queries, JdbcClient jdbc) {
		this.queries = queries;
		this.jdbc = jdbc;
	}

	/**
	 * {@code status}: verilmezse active; active/consumed disinda hepsi. {@code lineage}: root | root_raw |
	* root_opening | derived. {@code search}: materyal no (#12 ya da 12), not, urun tipi adi, parti kodu,
	* tank adi/kodu.
	 */
	MaterialListResponse index(String statusParam, String lineageParam, String searchParam) {
		String status = statusParam == null ? "active" : statusParam;
		status = STATUSES.contains(status) ? status : null;
		String lineage = lineageParam != null && LINEAGES.contains(lineageParam) ? lineageParam : null;
		String search = searchParam == null ? "" : searchParam.trim();
		List<MaterialRow> materials = search(status, lineage, search);

		Map<Long, ProductType> types = queries.productTypes(
			materials.stream().map(MaterialRow::productionProductTypeId).toList());
		Map<Long, Product> products = queries.products(
			types.values().stream().map(ProductType::productionProductId).toList());
		Map<Long, Batch> batches = queries.batches(materials.stream().map(MaterialRow::productionBatchId).toList());
		Map<Long, Unit> units = queries.units(materials.stream().map(MaterialRow::currentLocationId).toList());

		List<MaterialListResponse.Item> data = new ArrayList<>();
		for (MaterialRow m : materials) {
			ProductType type = lookup(types, m.productionProductTypeId());
			Batch batch = lookup(batches, m.productionBatchId());
			data.add(new MaterialListResponse.Item(m.id(), m.quantity(), m.unit(), m.status(), lineageRole(m),
				m.parentMaterialId(), m.originType(), type == null ? null : typeSummary(type, products),
				storageUnit(m, units), batch == null ? null : batch.batchCode(), m.createdAt()));
		}
		return new MaterialListResponse(data, new MaterialListResponse.Meta(LIST_LIMIT));
	}

	private List<MaterialRow> search(String status, String lineage, String search) {
		List<String> where = new ArrayList<>();
		Map<String, Object> params = new LinkedHashMap<>();
		if (status != null) {
			where.add("status = :status");
			params.put("status", status);
		}
		if (lineage != null) {
			switch (lineage) {
				case "root" -> where.add("parent_material_id is null");
				case "root_raw" -> where.add("parent_material_id is null and origin_type = 'BATCH_PROCESS'");
				case "root_opening" -> where.add("parent_material_id is null and origin_type = 'STORAGE_OPENING'");
				default -> where.add("parent_material_id is not null");
			}
		}
		if (!search.isEmpty()) {
			Long id = parseId(search);
			String idPart = id == null ? "" : "id = :id or ";
			if (id != null) {
				params.put("id", id);
			}
			params.put("like", "%" + search + "%");
			where.add("(" + idPart + """
				note::text ilike :like
				or exists (select * from production_product_types t
					where materials.production_product_type_id = t.id and t.name::text ilike :like)
				or exists (select * from production_batches b
					where materials.production_batch_id = b.id and b.batch_code::text ilike :like)
				or (current_location_type = 'storage_unit' and exists (select * from storage_units u
					where materials.current_location_id = u.id
						and (u.name::text ilike :like or u.code::text ilike :like))))
				""");
		}
		String sql = "select * from materials" + (where.isEmpty() ? "" : " where " + String.join(" and ", where))
			+ " order by id desc limit " + LIST_LIMIT;
		return jdbc.sql(sql).params(params).query(MaterialRow.class).list();
	}

	/** {@code #12} ya da {@code 12}: yalnizca rakamsa id. */
	private static Long parseId(String search) {
		String digits = search.replaceFirst("^#+", "");
		if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
			return null;
		}
		try {
			return Long.parseLong(digits);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	MaterialDetailResponse detail(long id) {
		MaterialRow m = queries.material(id).orElseThrow(() -> new ModelNotFoundException("Material", id));
		ProductType type = m.productionProductTypeId() == null ? null
			: queries.productTypes(List.of(m.productionProductTypeId())).get(m.productionProductTypeId());
		Batch batch = m.productionBatchId() == null ? null
			: queries.batches(List.of(m.productionBatchId())).get(m.productionBatchId());
		MaterialRow parent = m.parentMaterialId() == null ? null : queries.material(m.parentMaterialId()).orElse(null);
		List<MaterialRow> children = queries.childrenOf(m.id());
		List<Long> lineageTypeIds = new ArrayList<>(
			children.stream().map(MaterialRow::productionProductTypeId).toList());
		if (parent != null) {
			lineageTypeIds.add(parent.productionProductTypeId());
		}
		Map<Long, ProductType> lineageTypes = queries.productTypes(lineageTypeIds);
		Map<Long, Unit> units = queries.units(
			m.currentLocationId() == null ? List.of() : List.of(m.currentLocationId()));
		Map<Long, Product> products = type == null || type.productionProductId() == null ? Map.of()
			: queries.products(List.of(type.productionProductId()));

		return new MaterialDetailResponse(m.id(), m.quantity(), m.unit(), m.status(), m.originType(), m.note(),
			m.createdAt(),
			quantityBreakdown(m, children), type == null ? null : typeSummary(type, products),
			batch == null ? null : new MaterialDetailResponse.BatchSummary(batch.id(), batch.batchCode(), batch.status()),
			storageUnit(m, units), parent == null ? null : lineageSummary(parent, lineageTypes),
			children.stream().map(c -> lineageSummary(c, lineageTypes)).toList(), attributeLines(m.id()), movements(m.id()),
			runs(m.id()));
	}

	/** Kendi miktari + ayni tipteki cocuklara ayrilan = giren; aktifse kalan, degilse kapanan. */
	private static MaterialDetailResponse.QuantityBreakdown quantityBreakdown(MaterialRow m,
		List<MaterialRow> children) {
		BigDecimal own = m.quantity();
		BigDecimal splitOut = children.stream()
			.filter(c -> Objects.equals(c.productionProductTypeId(), m.productionProductTypeId()))
			.map(MaterialRow::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
		boolean active = "active".equals(m.status());
		return new MaterialDetailResponse.QuantityBreakdown(Quantities.number(own.add(splitOut)),
			Quantities.number(splitOut), Quantities.number(active ? BigDecimal.ZERO : own),
			Quantities.number(active ? own : BigDecimal.ZERO));
	}

	/**
	 * Etiket, birim ve ilk dolu deger (decimal > integer > text > boolean); tanimin sort_order'ina gore
	 * (kararli).
	 */
	private List<MaterialDetailResponse.AttributeLine> attributeLines(long materialId) {
		List<MaterialQueries.AttributeValueRow> values = queries.attributeValues(List.of(materialId));
		Map<Long, MaterialQueries.Definition> definitions = queries.definitions(
			values.stream().map(MaterialQueries.AttributeValueRow::definitionId).toList());
		List<MaterialQueries.AttributeValueRow> sorted = new ArrayList<>(values);
		// null sort_order (tanim yok) en basta
		sorted.sort(Comparator.comparingInt(v -> {
			MaterialQueries.Definition d = lookup(definitions, v.definitionId());
			return d == null || d.sortOrder() == null ? Integer.MIN_VALUE : d.sortOrder();
		}));
		return sorted.stream().map(v -> {
			MaterialQueries.Definition d = lookup(definitions, v.definitionId());
			Object value = v.valueDecimal() != null ? v.valueDecimal().toPlainString()
				: v.valueInteger() != null ? v.valueInteger()
				: v.valueText() != null ? v.valueText() : v.valueBoolean();
			return new MaterialDetailResponse.AttributeLine(d == null ? null : d.label(),
				d == null ? null : d.unit(), value);
		}).toList();
	}

	/** Son 20 hareket (moved_at desc, id desc). */
	private List<MaterialDetailResponse.Movement> movements(long materialId) {
		record Row(Long id, Long storageUnitId, Long counterpartyStorageUnitId, Long finishedProductRunId,
			String movementType, BigDecimal quantity, String unit, LocalDateTime movedAt) {
		}
		List<Row> rows = jdbc.sql("""
			select id, storage_unit_id, counterparty_storage_unit_id, finished_product_run_id, movement_type, quantity,
				unit, moved_at
			from storage_movements where material_id = :id order by moved_at desc, id desc
			""" + " limit " + MOVEMENT_LIMIT).param("id", materialId).query(Row.class).list();
		List<Long> unitIds = new ArrayList<>(rows.stream().map(Row::storageUnitId).toList());
		unitIds.addAll(rows.stream().map(Row::counterpartyStorageUnitId).toList());
		Map<Long, Unit> units = queries.units(unitIds);
		return rows.stream().map(r -> {
			Unit unit = lookup(units, r.storageUnitId());
			Unit counterparty = lookup(units, r.counterpartyStorageUnitId());
			return new MaterialDetailResponse.Movement(r.id(), r.finishedProductRunId(), r.movementType(), r.quantity(),
				r.unit(), r.movedAt(), unit == null ? null : unit.name(), counterparty == null ? null : counterparty.name());
		}).toList();
	}

	/** Bu materyalden cekilen dolum kosulari (created_at desc); kullanilan miktar bu materyalinki. */
	private List<MaterialDetailResponse.Run> runs(long materialId) {
		record RunRow(Long id, String status, Long finishedProductId, Integer quantity, LocalDateTime startedAt,
			LocalDateTime completedAt, LocalDateTime createdAt) {
		}
		record Used(Long runId, BigDecimal quantityUsed) {
		}
		record NameRow(Long id, String name) {
		}
		List<RunRow> rows = jdbc.sql("""
			select id, status, finished_product_id, quantity, started_at, completed_at, created_at
			from finished_product_runs r
			where exists (select * from finished_product_run_materials m
				where r.id = m.finished_product_run_id and m.source_material_id = :material)
			order by created_at desc
			""").param("material", materialId).query(RunRow.class).list();
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> runIds = rows.stream().map(RunRow::id).toList();
		Map<Long, BigDecimal> used = new LinkedHashMap<>();
		jdbc.sql("""
			select finished_product_run_id as run_id, quantity_used from finished_product_run_materials
			where finished_product_run_id in (:ids) and source_material_id = :material
			""").param("ids", runIds).param("material", materialId).query(Used.class).list()
			.forEach(u -> used.merge(u.runId(), u.quantityUsed(), BigDecimal::add));
		Map<Long, String> names = new LinkedHashMap<>();
		List<Long> productIds = rows.stream().map(RunRow::finishedProductId).filter(Objects::nonNull).distinct()
			.toList();
		if (!productIds.isEmpty()) {
			jdbc.sql("select id, name from finished_products where id in (:ids)").param("ids", productIds)
				.query(NameRow.class).list().forEach(n -> names.put(n.id(), n.name()));
		}
		return rows.stream().map(run -> {
			LocalDateTime date = run.completedAt() != null ? run.completedAt()
				: run.startedAt() != null ? run.startedAt() : run.createdAt();
			return new MaterialDetailResponse.Run(run.id(), run.status(), lookup(names, run.finishedProductId()),
				run.quantity(), Quantities.number(used.getOrDefault(run.id(), BigDecimal.ZERO)), date);
		}).toList();
	}

	/** Ebeveyn varsa derived, yoksa kokenden. */
	static String lineageRole(MaterialRow m) {
		if (m.parentMaterialId() != null) {
			return "derived";
		}
		return switch (m.originType() == null ? "" : m.originType()) {
			case "BATCH_PROCESS" -> "root_raw";
			case "STORAGE_OPENING" -> "root_opening";
			case "STORAGE_ADJUSTMENT" -> "root_adjustment";
			default -> "root_other";
		};
	}

	static TypeSummary typeSummary(ProductType type, Map<Long, Product> products) {
		Product product = lookup(products, type.productionProductId());
		return new TypeSummary(type.id(), type.name(), type.unit(), MaterialResponses.effectiveDensity(type, product));
	}

	/** Materyal tankta ise {id, name, code}, degilse null. */
	static MaterialResponse.UnitSummary storageUnit(MaterialRow m, Map<Long, Unit> units) {
		Unit unit = "storage_unit".equals(m.currentLocationType()) ? lookup(units, m.currentLocationId()) : null;
		return unit == null ? null : new MaterialResponse.UnitSummary(unit.id(), unit.name(), unit.code());
	}

	private static LineageSummary lineageSummary(MaterialRow m, Map<Long, ProductType> types) {
		ProductType type = lookup(types, m.productionProductTypeId());
		return new LineageSummary(m.id(), type == null ? null : type.name(), m.quantity(), m.unit(), m.status());
	}

}
