package com.olivamore.api.catalog;

import com.olivamore.api.shared.persistence.Db;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Katalog resource'larinin diger modullere acik kismi: baska bir resource'un icinde gecen asama uzantilari
 * ve proses alanlari (ornek: BatchProcessResource'taki {@code extensions}, {@code field_definitions}).
 */
@Component
public class CatalogResources {

	private final Db db;
	private final CatalogReads reads;

	CatalogResources(Db db, CatalogReads reads) {
		this.db = db;
		this.reads = reads;
	}

	/**
	 * ProductionStageExtensionResource'lar, asamaya gore (sort_order sirasinda): stageExtensionType, girdi ve
	 * cikti urunleri yuklu; {@code values} yuklu degil (anahtar yok), uzanti tipinin alanlari lazy.
	 */
	public Map<Long, List<Map<String, Object>>> stageExtensions(List<Long> stageIds) {
		List<Map<String, Object>> extensions = new java.util.ArrayList<>();
		for (Map<String, Object> e : reads.extensions(db.whereIn("production_stage_extensions", "production_stage_id", stageIds,
			"sort_order asc"), false)) {
			Map<String, Object> copy = new java.util.LinkedHashMap<>(e);
			copy.remove("values");
			extensions.add(copy);
		}
		return CatalogReads.group(extensions, "production_stage_id");
	}

	/** ProcessTypeFieldResource'lar, proses tipine gore (sort_order sirasinda). */
	public Map<Long, List<Map<String, Object>>> processTypeFields(List<Long> processTypeIds) {
		return CatalogReads.group(reads.fields(db.whereIn("process_type_fields", "process_type_id", processTypeIds,
			"sort_order asc")), "process_type_id");
	}

}
