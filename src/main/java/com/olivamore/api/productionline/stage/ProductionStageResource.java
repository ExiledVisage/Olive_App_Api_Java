package com.olivamore.api.productionline.stage;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.process.ProcessTypeResponse;
import com.olivamore.api.productionline.stageextension.ProductionStageExtensionResource;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionStageResource}: proses tipi (alanlariyla) ve istenirse uzantilar. */
@Component
public class ProductionStageResource {

	private final Db db;
	private final ProcessResources process;
	private final ProductionStageExtensionResource extensionResource;

	ProductionStageResource(Db db, ProcessResources process, ProductionStageExtensionResource extensionResource) {
		this.db = db;
		this.process = process;
		this.extensionResource = extensionResource;
	}

	/** Hatta gore (stage_order sirasinda); {@code stages} iliskisinin eager loading'i. */
	public Map<Long, List<Map<String, Object>>> byLine(List<Long> lineIds, boolean extensions, boolean typeFieldsLoaded) {
		return Relations.groupItems(stages(db.whereIn("production_stages", "production_line_id", lineIds, "stage_order asc"),
			extensions, typeFieldsLoaded), "production_line_id");
	}

	/**
	 * {@code extensions}: uzantilar yuklendiyse; {@code typeFieldsLoaded}: uzanti tipinin alanlari eager mi
	 * (degilse Laravel resource'ta tip basina lazy load eder, sonuc ayni).
	 */
	public List<Map<String, Object>> stages(List<Row> stages, boolean extensions, boolean typeFieldsLoaded) {
		Map<Long, ProcessTypeResponse> processTypes = process.processTypesById(
			stages.stream().map(st -> Relations.key(st.get("process_type_id"))).toList());
		Map<Long, List<Map<String, Object>>> stageExtensions = extensions
			? extensionResource.byStage(Relations.ids(stages), typeFieldsLoaded)
			: Map.of();

		List<Map<String, Object>> out = new ArrayList<>();
		for (Row stage : stages) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", stage.get("id"));
			r.put("production_line_id", stage.get("production_line_id"));
			r.put("process_type_id", stage.get("process_type_id"));
			r.put("name", stage.get("name"));
			r.put("stage_order", Casts.integer(stage.get("stage_order")));
			r.put("is_required", Casts.bool(stage.get("is_required")));
			r.put("requires_approval", Casts.bool(stage.get("requires_approval")));
			// ic ice new ProcessTypeResource(null) -> null
			Long typeId = Relations.key(stage.get("process_type_id"));
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

}
