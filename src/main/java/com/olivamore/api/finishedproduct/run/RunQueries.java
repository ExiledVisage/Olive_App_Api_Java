package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tank, materyal, urun tipi, ambalaj ve attribute tablolarindan salt okuma. */
@Component
class RunQueries {

	record Material(Long id, Long productionProductTypeId, BigDecimal quantity, String unit, String status,
		String currentLocationType, Long currentLocationId) {
	}

	record ProductType(Long id, String name, BigDecimal densityKgPerLitre, BigDecimal defaultDensityKgPerLitre) {

		/** Kendi yogunlugu > 0 ise o, yoksa kategorinin varsayilani > 0 ise o. */
		Double effectiveDensity() {
			return effective(densityKgPerLitre, defaultDensityKgPerLitre);
		}

	}

	record Unit(Long id, String name, String code) {
	}

	record MaterialAttribute(Long materialId, Long attributeDefinitionId, BigDecimal valueDecimal) {
	}

	record MaterialDisplay(Long id, Long runId, Long sourceMaterialId, BigDecimal quantityUsed, String unit,
		Long productionProductTypeId, String typeName, BigDecimal densityKgPerLitre,
		BigDecimal defaultDensityKgPerLitre) {
	}

	record PackagingDisplay(Long id, Long runId, Long packagingItemId, String packagingItemName,
		String packagingItemType,
		Long packagingLotId, String lotCode, LocalDateTime lotReceivedAt, Integer lotRemaining, String supplierName,
		int quantity) {
	}

	record ValueDisplay(Long id, Long runId, Long attributeDefinitionId, String attributeLabel,
		BigDecimal estimatedValue,
		BigDecimal actualValue) {
	}

	private final JdbcClient jdbc;

	RunQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Unit> unit(long id) {
		return jdbc.sql("select id, name, code from storage_units where id = :id").param("id", id)
			.query(Unit.class).optional();
	}

	Map<Long, Unit> units(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, code from storage_units where id in (:ids)").param("ids", present)
			.query(Unit.class).list().stream().collect(Collectors.toMap(Unit::id, Function.identity()));
	}

	boolean unitExists(long id) {
		return jdbc.sql("select count(*) from storage_units where id = :id").param("id", id)
			.query(Long.class).single() > 0;
	}

	/** Tanktaki aktif materyaller. */
	List<Material> activeInTank(long unitId) {
		return jdbc.sql("""
			select id, production_product_type_id, quantity, unit, status, current_location_type, current_location_id
			from materials where current_location_type = 'storage_unit' and current_location_id = :unit
				and status = 'active'
			""").param("unit", unitId).query(Material.class).list();
	}

	Map<Long, Material> materials(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select id, production_product_type_id, quantity, unit, status, current_location_type, current_location_id
			from materials where id in (:ids)
			""").param("ids", present).query(Material.class).list().stream()
			.collect(Collectors.toMap(Material::id, Function.identity()));
	}

	Map<Long, ProductType> productTypes(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select t.id, t.name, t.density_kg_per_litre, p.default_density_kg_per_litre
			from production_product_types t
			left join production_products p on p.id = t.production_product_id
			where t.id in (:ids)
			""").param("ids", present).query(ProductType.class).list().stream()
			.collect(Collectors.toMap(ProductType::id, Function.identity()));
	}

	/** Malzeme attribute degerleri, global attribute tanimina cevrilmis. */
	List<MaterialAttribute> materialAttributes(Collection<Long> materialIds) {
		List<Long> present = ValidationErrors.present(materialIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select v.material_id, d.attribute_definition_id, v.value_decimal
			from material_attribute_values v
			join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id
			where v.material_id in (:ids)
			""").param("ids", present).query(MaterialAttribute.class).list();
	}

	Map<Long, String> attributeLabels(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		record LabelRow(Long id, String label) {
		}
		return jdbc.sql("select id, label from attribute_definitions where id in (:ids)").param("ids", present)
			.query(LabelRow.class).list().stream().collect(Collectors.toMap(LabelRow::id, LabelRow::label));
	}

	String packagingItemName(long itemId) {
		return jdbc.sql("select name from packaging_items where id = :id").param("id", itemId)
			.query(String.class).optional().orElse("");
	}

	Collection<Long> existingLots(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select id from packaging_lots where id in (:ids)").param("ids", present)
			.query(Long.class).list();
	}

	boolean hasAllocations(long runId) {
		return jdbc.sql("select count(*) from finished_product_run_packagings where finished_product_run_id = :id")
			.param("id", runId).query(Long.class).single() > 0;
	}

	List<MaterialDisplay> materialDisplays(Collection<Long> runIds) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select rm.id, rm.finished_product_run_id as run_id, rm.source_material_id, rm.quantity_used, m.unit,
				m.production_product_type_id, t.name as type_name, t.density_kg_per_litre, p.default_density_kg_per_litre
			from finished_product_run_materials rm
			left join materials m on m.id = rm.source_material_id
			left join production_product_types t on t.id = m.production_product_type_id
			left join production_products p on p.id = t.production_product_id
			where rm.finished_product_run_id in (:ids)
			order by rm.id
			""").param("ids", runIds).query(MaterialDisplay.class).list();
	}

	List<PackagingDisplay> packagingDisplays(Collection<Long> runIds) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select p.id, p.finished_product_run_id as run_id, p.packaging_item_id, i.name as packaging_item_name,
				i.type as packaging_item_type, p.packaging_lot_id, l.lot_code, l.received_at as lot_received_at,
				l.quantity_remaining as lot_remaining, s.name as supplier_name, p.quantity
			from finished_product_run_packagings p
			left join packaging_items i on i.id = p.packaging_item_id
			left join packaging_lots l on l.id = p.packaging_lot_id
			left join product_suppliers s on s.id = l.product_supplier_id
			where p.finished_product_run_id in (:ids)
			order by p.id
			""").param("ids", runIds).query(PackagingDisplay.class).list();
	}

	List<ValueDisplay> valueDisplays(Collection<Long> runIds) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select v.id, v.finished_product_run_id as run_id, v.attribute_definition_id, d.label as attribute_label,
				v.estimated_value, v.actual_value
			from finished_product_run_attribute_values v
			left join attribute_definitions d on d.id = v.attribute_definition_id
			where v.finished_product_run_id in (:ids)
			order by v.id
			""").param("ids", runIds).query(ValueDisplay.class).list();
	}

	/** Kendi yogunlugu > 0 ise o, yoksa kategorinin varsayilani > 0 ise o; hic yoksa {@code null}. */
	static Double effective(BigDecimal own, BigDecimal fallback) {
		if (own != null && own.signum() > 0) {
			return own.doubleValue();
		}
		return fallback != null && fallback.signum() > 0 ? fallback.doubleValue() : null;
	}

}
