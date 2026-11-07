package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.olivamore.api.inventory.material.MaterialResource;

/**
 * Laravel {@code StorageMovementResource}: material (tip + attribute degerleri), batchProcess, karsi tank; tankin
 * kendisi yalnizca istendiginde.
 */
@Component
public class StorageMovementResource {

	private final Db db;
	private final MaterialResource materials;

	StorageMovementResource(Db db, MaterialResource materials) {
		this.db = db;
		this.materials = materials;
	}

	/**
	 * StorageUnitService::getMovements iliskileri: material (tip + attribute degerleri), batchProcess,
	 * counterpartyStorageUnit. storageUnit yuklu degil (anahtar yok).
	 */
	public List<Map<String, Object>> movements(List<Row> movements) {
		return movements(movements, false);
	}

	/** @param withStorageUnit loadMovementRelations (yazma cevaplari): storageUnit de yuklu */
	public List<Map<String, Object>> movements(List<Row> movements, boolean withStorageUnit) {
		List<Row> materialRows = db.whereIn("materials", "id", Relations.column(movements, "material_id"), null);
		List<Map<String, Object>> materialResources = materials.materials(materialRows, MaterialResource.MaterialLoad.TYPE_AND_ATTRIBUTES);
		Map<Long, Map<String, Object>> materials = new LinkedHashMap<>();
		for (int i = 0; i < materialRows.size(); i++) {
			materials.put(materialRows.get(i).id(), materialResources.get(i));
		}
		Map<Long, Row> processes = Relations.byId(db.whereIn("batch_processes", "id", Relations.column(movements, "batch_process_id"), null));
		Map<Long, Row> counterparties = Relations.byId(db.whereIn("storage_units", "id",
			Relations.column(movements, "counterparty_storage_unit_id"), null));
		Map<Long, Row> units = withStorageUnit
			? Relations.byId(db.whereIn("storage_units", "id", Relations.column(movements, "storage_unit_id"), null))
			: Map.of();

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row m : movements) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", m.get("id"));
			r.put("storage_unit_id", m.get("storage_unit_id"));
			r.put("material_id", m.get("material_id"));
			r.put("batch_process_id", m.get("batch_process_id"));
			r.put("finished_product_run_id", m.get("finished_product_run_id"));
			r.put("counterparty_storage_unit_id", m.get("counterparty_storage_unit_id"));
			r.put("source_type", m.get("source_type"));
			r.put("movement_type", m.get("movement_type"));
			r.put("transfer_reference", m.get("transfer_reference"));
			r.put("quantity", Casts.decimal(m.get("quantity"), 2));
			r.put("unit", m.get("unit"));
			r.put("note", m.get("note"));
			r.put("moved_at", m.get("moved_at"));
			if (withStorageUnit) {
				Row unit = units.get(Relations.key(m.get("storage_unit_id")));
				r.put("storage_unit", unit == null ? null : unitSummary(unit));
			}
			r.put("material", materials.get(Relations.key(m.get("material_id"))));
			Row p = processes.get(Relations.key(m.get("batch_process_id")));
			r.put("batch_process", p == null ? null : batchProcess(p));
			Row c = counterparties.get(Relations.key(m.get("counterparty_storage_unit_id")));
			r.put("counterparty_storage_unit", c == null ? null : unitSummary(c));
			r.put("created_at", m.get("created_at"));
			r.put("updated_at", m.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	static Map<String, Object> batchProcess(Row p) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", p.get("id"));
		r.put("production_batch_id", p.get("production_batch_id"));
		r.put("production_stage_id", p.get("production_stage_id"));
		r.put("status", p.get("status"));
		return r;
	}

	public static Map<String, Object> unitSummary(Row u) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", u.get("id"));
		r.put("name", u.get("name"));
		r.put("code", u.get("code"));
		return r;
	}

}
