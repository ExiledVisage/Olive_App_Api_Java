package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.inventory.MaterialDirectory;
import com.olivamore.api.packaging.PackagingDirectory;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.product.UnitConversion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Kosu cevaplari ve kurallari icin okumalar: tank, materyal, urun ve ambalaj bilgileri o modullerden. */
@Component
class RunQueries {

	record Material(Long id, Long productionProductTypeId, BigDecimal quantity, String unit, String status,
		String currentLocationType, Long currentLocationId) {
	}

	record ProductType(Long id, String name, BigDecimal densityKgPerLitre, BigDecimal defaultDensityKgPerLitre) {

		/** Kendi yogunlugu > 0 ise o, yoksa kategorinin varsayilani > 0 ise o. */
		BigDecimal effectiveDensity() {
			return UnitConversion.effectiveDensity(densityKgPerLitre, defaultDensityKgPerLitre);
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
	private final MaterialDirectory materialDirectory;
	private final ProductDirectory productDirectory;
	private final PackagingDirectory packaging;

	RunQueries(JdbcClient jdbc, MaterialDirectory materialDirectory, ProductDirectory productDirectory,
		PackagingDirectory packaging) {
		this.jdbc = jdbc;
		this.materialDirectory = materialDirectory;
		this.productDirectory = productDirectory;
		this.packaging = packaging;
	}

	private record RunMaterialRow(Long id, Long runId, Long sourceMaterialId, BigDecimal quantityUsed) {
	}

	private record ValueRow(Long id, Long runId, Long attributeDefinitionId, BigDecimal estimatedValue,
		BigDecimal actualValue) {
	}

	Optional<Unit> unit(long id) {
		return materialDirectory.tank(id).map(RunQueries::unit);
	}

	Map<Long, Unit> units(Collection<Long> ids) {
		return materialDirectory.tanks(ids).values().stream()
			.collect(Collectors.toMap(MaterialDirectory.Tank::id, RunQueries::unit));
	}

	private static Unit unit(MaterialDirectory.Tank tank) {
		return new Unit(tank.id(), tank.name(), tank.code());
	}

	boolean unitExists(long id) {
		return materialDirectory.tankExists(id);
	}

	/** Tanktaki aktif materyaller. */
	List<Material> activeInTank(long unitId) {
		return materialDirectory.activeInTank(unitId).stream().map(RunQueries::material).toList();
	}

	Map<Long, Material> materials(Collection<Long> ids) {
		return materialDirectory.materials(ids).values().stream()
			.collect(Collectors.toMap(MaterialDirectory.Material::id, RunQueries::material));
	}

	private static Material material(MaterialDirectory.Material m) {
		return new Material(m.id(), m.productionProductTypeId(), m.quantity(), m.unit(), m.status(),
			m.currentLocationType(), m.currentLocationId());
	}

	Map<Long, ProductType> productTypes(Collection<Long> ids) {
		return productDirectory.productTypes(ids).values().stream().collect(Collectors.toMap(
			ProductDirectory.ProductType::id,
			t -> new ProductType(t.id(), t.name(), t.densityKgPerLitre(), t.defaultDensityKgPerLitre())));
	}

	/** Malzeme attribute degerleri, global attribute tanimina cevrilmis. */
	List<MaterialAttribute> materialAttributes(Collection<Long> materialIds) {
		List<MaterialDirectory.DecimalValue> values = materialDirectory.decimalValues(materialIds);
		Map<Long, ProductDirectory.AttributeMapping> mappings = productDirectory.attributeMappings(
			values.stream().map(MaterialDirectory.DecimalValue::mappingId).toList());
		return values.stream().filter(v -> mappings.containsKey(v.mappingId()))
			.map(v -> new MaterialAttribute(v.materialId(), mappings.get(v.mappingId()).attributeDefinitionId(),
				v.valueDecimal()))
			.toList();
	}

	Map<Long, String> attributeLabels(Collection<Long> ids) {
		return productDirectory.attributeLabels(ids);
	}

	String packagingItemName(long itemId) {
		return packaging.itemName(itemId);
	}

	Collection<Long> existingLots(Collection<Long> ids) {
		return packaging.existingLots(ids);
	}

	boolean hasAllocations(long runId) {
		return packaging.hasAllocations(runId);
	}

	List<MaterialDisplay> materialDisplays(Collection<Long> runIds) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		List<RunMaterialRow> rows = jdbc.sql("""
			select id, finished_product_run_id as run_id, source_material_id, quantity_used
			from finished_product_run_materials where finished_product_run_id in (:ids) order by id
			""").param("ids", runIds).query(RunMaterialRow.class).list();
		Map<Long, MaterialDirectory.Material> materials = materialDirectory.materials(
			rows.stream().map(RunMaterialRow::sourceMaterialId).toList());
		Map<Long, ProductDirectory.ProductType> types = productDirectory.productTypes(materials.values().stream()
			.map(MaterialDirectory.Material::productionProductTypeId).toList());
		return rows.stream().map(row -> {
			MaterialDirectory.Material m = materials.get(row.sourceMaterialId());
			ProductDirectory.ProductType t = m == null ? null : types.get(m.productionProductTypeId());
			return new MaterialDisplay(row.id(), row.runId(), row.sourceMaterialId(), row.quantityUsed(),
				m == null ? null : m.unit(), m == null ? null : m.productionProductTypeId(), t == null ? null : t.name(),
				t == null ? null : t.densityKgPerLitre(), t == null ? null : t.defaultDensityKgPerLitre());
		}).toList();
	}

	List<PackagingDisplay> packagingDisplays(Collection<Long> runIds) {
		return packaging.runPackagings(runIds).stream()
			.map(p -> new PackagingDisplay(p.id(), p.runId(), p.packagingItemId(), p.packagingItemName(),
				p.packagingItemType(), p.packagingLotId(), p.lotCode(), p.lotReceivedAt(), p.lotRemaining(),
				p.supplierName(), p.quantity()))
			.toList();
	}

	List<ValueDisplay> valueDisplays(Collection<Long> runIds) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		List<ValueRow> rows = jdbc.sql("""
			select id, finished_product_run_id as run_id, attribute_definition_id, estimated_value, actual_value
			from finished_product_run_attribute_values where finished_product_run_id in (:ids) order by id
			""").param("ids", runIds).query(ValueRow.class).list();
		Map<Long, String> labels = productDirectory.attributeLabels(
			rows.stream().map(ValueRow::attributeDefinitionId).toList());
		return rows.stream().map(row -> new ValueDisplay(row.id(), row.runId(), row.attributeDefinitionId(),
			labels.get(row.attributeDefinitionId()), row.estimatedValue(), row.actualValue())).toList();
	}

	/** Kendi yogunlugu > 0 ise o, yoksa kategorinin varsayilani > 0 ise o; hic yoksa {@code null}. */
	static Double effective(BigDecimal own, BigDecimal fallback) {
		BigDecimal density = UnitConversion.effectiveDensity(own, fallback);
		return density == null ? null : density.doubleValue();
	}

}
