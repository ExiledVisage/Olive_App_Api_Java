package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.CompareSubject.Item;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Karsilastirma sorgulari (docs/COMPARE.md): kalem adlari, teslimatlar, IAS, tank hareketleri, makine calismalari. */
@Repository
class CompareQueries {

	/** Teslimat (grup degil): varis, kilo, alim bilgisi, ilk girdi urun tipi. */
	record Delivery(long id, Long supplierId, Long supplierFieldId, Long productionLineId, Long productTypeId,
		LocalDateTime receivedAt, BigDecimal quantityKg, String supplyChannel, BigDecimal pricePerKg,
		BigDecimal vatRate, BigDecimal transportCost) {
	}

	record IasValue(long batchId, String productKey, String attributeKey, BigDecimal valueDecimal) {
	}

	/** Tank hareketi: giris, cikis, aktarma, acilis; kilo materyalin yogunluguyla litreye (yogunluk yoksa null). */
	record Movement(long storageUnitId, String movementType, BigDecimal litres, LocalDateTime movedAt,
		long materialId) {
	}

	record TankState(long id, BigDecimal capacity, BigDecimal current) {
	}

	/** Makine calismasi: hangi makine, hangi parti, ne zaman. */
	record MachineRun(long machineId, long batchId, LocalDateTime startedAt, LocalDateTime finishedAt,
		BigDecimal capacityKgPerHour) {
	}

	record FinishedRun(long finishedProductId, long quantity, LocalDateTime completedAt) {
	}

	/** Kalem adlari: {@code {ids}} yerine id suzgeci ya da hic suzgec gelir. */
	private static final Map<String, String> NAMES = Map.of(
		"supplier", "select s.id::text as id, s.name, s.code as detail from suppliers s where true {ids}",
		"field", """
			select f.id::text as id, s.name || ' — ' || f.code as name, f.location as detail
			from supplier_fields f join suppliers s on s.id = f.supplier_id where true {ids}""",
		"product_type", """
			select t.id::text as id, t.name, p.name as detail
			from production_product_types t join production_products p on p.id = t.production_product_id
			where true {ids}""",
		"delivery", """
			select b.id::text as id, b.batch_code as name,
				concat_ws(' · ', s.name, to_char(b.received_at, 'DD.MM.YYYY')) as detail
			from production_batches b left join suppliers s on s.id = b.supplier_id
			where b.parent_batch_id is null {ids}""",
		"line", "select l.id::text as id, l.name, l.description as detail from production_lines l where true {ids}",
		"customer", """
			select c.id::text as id,
				case when coalesce(c.company_name, '') in ('', '0') then c.name else c.company_name end as name,
				case when coalesce(c.company_name, '') in ('', '0') then null else c.name end as detail
			from customers c where true {ids}""",
		"finished_product", """
			select p.id::text as id, p.name, trim_scale(p.volume_litre)::text || ' L' as detail
			from finished_products p where true {ids}""",
		"tank", "select u.id::text as id, u.name, u.code as detail from storage_units u where true {ids}",
		"machine", """
			select m.id::text as id, m.name, concat_ws(' · ', m.code, t.name) as detail
			from machines m join machine_types t on t.id = m.machine_type_id where true {ids}""");

	private static final Map<String, String> ID_COLUMN = Map.of("supplier", "s.id", "field", "f.id", "product_type",
		"t.id", "delivery", "b.id", "line", "l.id", "customer", "c.id", "finished_product", "p.id", "tank", "u.id",
		"machine", "m.id");

	private final JdbcClient jdbc;

	CompareQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Verilen kimliklerin adlari (bulunamayan dusurulur). */
	List<Item> names(String subject, List<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		String sql = NAMES.get(subject).replace("{ids}", "and " + ID_COLUMN.get(subject) + " in (:ids)");
		return jdbc.sql(sql).param("ids", ids).query(Item.class).list();
	}

	/** Gruptaki butun kalemler, ada gore. */
	List<Item> all(String subject) {
		return jdbc.sql(NAMES.get(subject).replace("{ids}", "") + " order by name").query(Item.class).list();
	}

	/** Aralikta gelen teslimatlar (urun tipi verilirse o girdiyle). */
	List<Delivery> deliveries(Range range, Long productTypeId) {
		return jdbc.sql("""
			select b.id, b.supplier_id, b.supplier_field_id, b.production_line_id,
				(select i.production_product_type_id from production_batch_input_production_product_types i
					where i.production_batch_id = b.id order by i.sort_order, i.id limit 1) as product_type_id,
				b.received_at, b.quantity_kg, b.supply_channel, b.price_per_kg, b.vat_rate, b.transport_cost
			from production_batches b
			where b.parent_batch_id is null and b.received_at between :from and :to
				and (cast(:type as bigint) is null or exists (
					select 1 from production_batch_input_production_product_types i
					where i.production_batch_id = b.id and i.production_product_type_id = :type))
			order by b.received_at, b.id
			""").param("from", range.from()).param("to", range.to()).param("type", productTypeId)
			.query(Delivery.class).list();
	}

	/** Parti -> teslimati (grup ise ust parti, degilse kendisi). */
	Map<Long, Long> deliveryIds(List<Long> batchIds) {
		Map<Long, Long> out = new HashMap<>();
		if (batchIds.isEmpty()) {
			return out;
		}
		jdbc.sql("select id, coalesce(parent_batch_id, id) from production_batches where id in (:ids)")
			.param("ids", batchIds).query((rs, n) -> Map.entry(rs.getLong(1), rs.getLong(2))).list()
			.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	/**
	 * Partilerin IAS degerleri (zeytin ve pirina: yag orani, KPY, endustriyel randiman) ve laboratuvar degerleri
	 * (zeytinyagi: peroksit, K232); partinin o urundeki en son materyalinden.
	 */
	List<IasValue> iasValues(List<Long> batchIds) {
		if (batchIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select distinct on (m.production_batch_id, p.key, a.attribute_key)
				m.production_batch_id as batch_id, p.key as product_key, a.attribute_key, v.value_decimal
			from materials m
			join production_product_types t on t.id = m.production_product_type_id
			join production_products p on p.id = t.production_product_id
			join material_attribute_values v on v.material_id = m.id
			join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
			join attribute_definitions a on a.id = d.attribute_definition_id
			where m.production_batch_id in (:ids) and p.key in ('olive', 'pomace', 'olive_oil')
				and a.attribute_key in ('oil_ratio', 'kpy', 'industrial_yield', 'peroxide', 'k232')
				and v.value_decimal is not null
			order by m.production_batch_id, p.key, a.attribute_key, m.id desc
			""").param("ids", batchIds).query(IasValue.class).list();
	}

	/** Parti -> zeytinyaginin sinifi (docs/QUALITY.md 1), partinin en son zeytinyagi materyalinden. */
	Map<Long, String> oilClasses(List<Long> batchIds) {
		Map<Long, String> out = new HashMap<>();
		if (batchIds.isEmpty()) {
			return out;
		}
		jdbc.sql("""
			select distinct on (m.production_batch_id) m.production_batch_id, v.value_text
			from materials m
			join production_product_types t on t.id = m.production_product_type_id
			join production_products p on p.id = t.production_product_id
			join material_attribute_values v on v.material_id = m.id
			join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
			join attribute_definitions a on a.id = d.attribute_definition_id
			where m.production_batch_id in (:ids) and p.key = 'olive_oil' and a.attribute_key = 'oil_class'
				and coalesce(v.value_text, '') <> ''
			order by m.production_batch_id, m.id desc
			""").param("ids", batchIds).query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
			.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	List<Movement> movements(Range range, List<Long> tankIds) {
		return jdbc.sql("""
			select sm.storage_unit_id, sm.movement_type, case when sm.unit = 'LITRE' then sm.quantity
					else sm.quantity / nullif(coalesce(t.density_kg_per_litre, p.default_density_kg_per_litre), 0)
				end as litres, sm.moved_at, sm.material_id
			from storage_movements sm
			join materials m on m.id = sm.material_id
			left join production_product_types t on t.id = m.production_product_type_id
			left join production_products p on p.id = t.production_product_id
			where sm.storage_unit_id in (:ids) and sm.moved_at between :from and :to
			order by sm.id
			""").param("ids", tankIds).param("from", range.from()).param("to", range.to())
			.query(Movement.class).list();
	}

	/** Materyal -> asitlik. */
	Map<Long, Double> acidity(List<Long> materialIds) {
		Map<Long, Double> out = new HashMap<>();
		if (materialIds.isEmpty()) {
			return out;
		}
		jdbc.sql("""
			select v.material_id, v.value_decimal from material_attribute_values v
			join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
			join attribute_definitions a on a.id = d.attribute_definition_id
			where a.attribute_key = 'acidity' and v.value_decimal is not null and v.material_id in (:ids)
			""").param("ids", materialIds)
			.query((rs, n) -> Map.entry(rs.getLong(1), rs.getBigDecimal(2).doubleValue())).list()
			.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	List<TankState> tankStates(List<Long> tankIds) {
		return jdbc.sql("""
			select u.id, u.capacity,
				(select coalesce(sum(case when b.unit = 'LITRE' then b.current_quantity
					else b.current_quantity / nullif(p.default_density_kg_per_litre, 0) end), 0)
					from storage_unit_balances b left join production_products p on p.id = b.production_product_id
					where b.storage_unit_id = u.id) as current
			from storage_units u where u.id in (:ids)
			""").param("ids", tankIds).query(TankState.class).list();
	}

	List<MachineRun> machineRuns(Range range, List<Long> machineIds) {
		return jdbc.sql("""
			select p.machine_id, bp.production_batch_id as batch_id, r.started_at, r.finished_at,
				m.capacity_kg_per_hour
			from machine_runs r
			join machine_parts p on p.id = r.machine_part_id
			join machines m on m.id = p.machine_id
			join batch_processes bp on bp.id = r.batch_process_id
			where p.machine_id in (:ids) and r.started_at between :from and :to
			order by r.id
			""").param("ids", machineIds).param("from", range.from()).param("to", range.to())
			.query(MachineRun.class).list();
	}

	List<FinishedRun> finishedRuns(Range range) {
		return jdbc.sql("""
			select finished_product_id, quantity, completed_at from finished_product_runs
			where status = 'completed' and completed_at between :from and :to
			""").param("from", range.from()).param("to", range.to()).query(FinishedRun.class).list();
	}

}
