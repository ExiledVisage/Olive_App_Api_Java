package com.olivamore.api.inventory;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Materyaller ve tanklar: diger modullerin okudugu tek kapi; satirlar her seferinde veritabanindan okunur. */
@Component
public class MaterialDirectory {

	public record Material(Long id, Long productionBatchId, Long productionProductTypeId, BigDecimal quantity,
		String unit, String status, String currentLocationType, Long currentLocationId) {
	}

	public record Tank(Long id, String name, String code) {
	}

	public record BatchMaterial(Long id, Long productionBatchId) {
	}

	/** Materyalin decimal attribute degeri; {@code mappingId} urun-attribute eslemesi. */
	public record DecimalValue(Long materialId, Long mappingId, BigDecimal valueDecimal) {
	}

	private record ProcessLink(Long id, Long ownerId) {
	}

	private static final String COLUMNS = "select id, production_batch_id, production_product_type_id, quantity, unit,"
		+ " status, current_location_type, current_location_id from materials ";

	private final JdbcClient jdbc;

	MaterialDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ---- materyaller ------------------------------------------------------

	public Optional<Material> material(long id) {
		return jdbc.sql(COLUMNS + "where id = :id").param("id", id).query(Material.class).optional();
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, Material> materials(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql(COLUMNS + "where id in (:ids)").param("ids", present).query(Material.class).list().stream()
			.collect(Collectors.toMap(Material::id, Function.identity()));
	}

	public List<Material> activeInTank(long tankId) {
		return jdbc.sql(COLUMNS + "where current_location_type = 'storage_unit' and current_location_id = :tank"
			+ " and status = 'active'").param("tank", tankId).query(Material.class).list();
	}

	public List<Material> activeOfBatch(long batchId) {
		return jdbc.sql(COLUMNS + "where production_batch_id = :batch and status = 'active'").param("batch", batchId)
			.query(Material.class).list();
	}

	/** Partinin verilen tiplerdeki aktif materyalleri. */
	public List<Material> activeOfBatch(long batchId, Collection<Long> typeIds) {
		List<Long> present = ValidationErrors.present(typeIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql(COLUMNS + "where production_batch_id = :batch and status = 'active'"
				+ " and production_product_type_id in (:types)")
			.param("batch", batchId).param("types", present).query(Material.class).list();
	}

	public Optional<Material> activeOfBatchAndType(long batchId, long typeId) {
		return jdbc.sql(COLUMNS + "where production_batch_id = :batch and production_product_type_id = :type"
				+ " and status = 'active' limit 1")
			.param("batch", batchId).param("type", typeId).query(Material.class).optional();
	}

	public List<BatchMaterial> ofBatches(Collection<Long> batchIds) {
		if (batchIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select id, production_batch_id from materials where production_batch_id in (:ids)")
			.param("ids", batchIds).query(BatchMaterial.class).list();
	}

	public long countOfBatch(long batchId) {
		return jdbc.sql("select count(*) from materials where production_batch_id = :id").param("id", batchId)
			.query(Long.class).single();
	}

	/** Proses tarafindan yaratilan materyaller, satir sirasiyla. */
	public Map<Long, List<Long>> createdBy(Collection<Long> processIds) {
		return links("created_by_batch_process_id", processIds);
	}

	/** Proses tarafindan kapatilan materyaller, satir sirasiyla. */
	public Map<Long, List<Long>> closedBy(Collection<Long> processIds) {
		return links("closed_by_batch_process_id", processIds);
	}

	private Map<Long, List<Long>> links(String column, Collection<Long> processIds) {
		if (processIds.isEmpty()) {
			return Collections.emptyMap();
		}
		List<ProcessLink> rows = jdbc.sql("select id, " + column + " as owner_id from materials where " + column
			+ " in (:ids)").param("ids", processIds).query(ProcessLink.class).list();
		return rows.stream().collect(Collectors.groupingBy(ProcessLink::ownerId, LinkedHashMap::new,
			Collectors.mapping(ProcessLink::id, Collectors.toList())));
	}

	public long countCreatedBy(long processId) {
		return jdbc.sql("select count(*) from materials where created_by_batch_process_id = :id").param("id", processId)
			.query(Long.class).single();
	}

	/** Materyallerin decimal attribute degerleri. */
	public List<DecimalValue> decimalValues(Collection<Long> materialIds) {
		List<Long> present = ValidationErrors.present(materialIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select material_id, production_product_attribute_definition_id as mapping_id, value_decimal
			from material_attribute_values where material_id in (:ids)
			""").param("ids", present).query(DecimalValue.class).list();
	}

	// ---- stok hareketleri --------------------------------------------------

	/** Proseslerin stok hareketi sayisi. */
	public long countMovementsOf(Collection<Long> processIds) {
		if (processIds.isEmpty()) {
			return 0;
		}
		return jdbc.sql("select count(*) from storage_movements where batch_process_id in (:ids)")
			.param("ids", processIds).query(Long.class).single();
	}

	// ---- tanklar -----------------------------------------------------------

	public boolean tankExists(long id) {
		return jdbc.sql("select count(*) from storage_units where id = :id").param("id", id)
			.query(Long.class).single() > 0;
	}

	public Optional<Tank> tank(long id) {
		return Optional.ofNullable(tanks(List.of(id)).get(id));
	}

	public Map<Long, Tank> tanks(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, code from storage_units where id in (:ids)").param("ids", present)
			.query(Tank.class).list().stream().collect(Collectors.toMap(Tank::id, Function.identity()));
	}

}
