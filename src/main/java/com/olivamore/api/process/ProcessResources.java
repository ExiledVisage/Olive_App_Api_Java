package com.olivamore.api.process;

import com.olivamore.api.process.processtype.ProcessTypeResponses;
import com.olivamore.api.process.processtypefield.ProcessTypeFieldResource;
import com.olivamore.api.process.stageextensiontype.StageExtensionTypeResource;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Proses resource'larinin diger modullere acik kismi: baska bir resource'un icinde gecen proses tipleri,
 * proses alanlari ve uzanti tipi alanlari (ornek: ProductionStageResource'taki {@code process_type},
 * BatchProcessResource'taki {@code field_definitions}).
 */
@Component
public class ProcessResources {

	private final ProcessTypeResponses typeResponses;
	private final ProcessTypeFieldResource fieldResource;
	private final StageExtensionTypeResource extensionTypeResource;

	ProcessResources(ProcessTypeResponses typeResponses, ProcessTypeFieldResource fieldResource,
		StageExtensionTypeResource extensionTypeResource) {
		this.typeResponses = typeResponses;
		this.fieldResource = fieldResource;
		this.extensionTypeResource = extensionTypeResource;
	}

	/** Proses tipleri (alanlari yuklu), id'ye gore; bulunamayan id'ler haritada yok. */
	public Map<Long, ProcessTypeResponse> processTypesById(List<Long> ids) {
		return typeResponses.byIds(ids);
	}

	/** ProcessTypeFieldResource'lar, proses tipine gore (sort_order sirasinda). */
	public Map<Long, List<Map<String, Object>>> processTypeFields(List<Long> processTypeIds) {
		return fieldResource.byProcessType(processTypeIds);
	}

	/** StageExtensionTypeFieldResource'lar, uzanti tipine gore (sort_order sirasinda). */
	public Map<Long, List<Map<String, Object>>> extensionTypeFields(List<Long> extensionTypeIds) {
		return extensionTypeResource.fieldsByType(extensionTypeIds, "sort_order asc");
	}

	/** StageExtensionTypeFieldResource */
	public Map<String, Object> extensionTypeField(Row field) {
		return StageExtensionTypeResource.field(field);
	}

}
