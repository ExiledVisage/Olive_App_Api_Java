package com.olivamore.api.process;

import com.olivamore.api.process.processtype.ProcessTypeResponses;
import com.olivamore.api.process.processtypefield.ProcessTypeFieldResponses;
import com.olivamore.api.process.stageextensiontype.StageExtensionTypeResponses;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Proses cevaplarinin diger modullere acik kismi: uretim hattinin asamalarindaki proses tipleri,
 * parti proseslerindeki alan tanimlari ve uzanti tipi alanlari.
 */
@Component
public class ProcessResources {

	private final ProcessTypeResponses types;
	private final ProcessTypeFieldResponses fields;
	private final StageExtensionTypeResponses extensionTypes;

	ProcessResources(ProcessTypeResponses types, ProcessTypeFieldResponses fields,
		StageExtensionTypeResponses extensionTypes) {
		this.types = types;
		this.fields = fields;
		this.extensionTypes = extensionTypes;
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, ProcessTypeResponse> processTypesById(List<Long> ids) {
		return types.byIds(ids);
	}

	/** Proses tipine gore, sort_order sirasinda. */
	public Map<Long, List<ProcessTypeFieldResponse>> processTypeFields(List<Long> processTypeIds) {
		return fields.byProcessType(processTypeIds);
	}

	/** Uzanti tipine gore, sort_order sirasinda. */
	public Map<Long, List<StageExtensionTypeFieldResponse>> extensionTypeFields(List<Long> extensionTypeIds) {
		return extensionTypes.fieldsByType(extensionTypeIds);
	}

	/** Alanlariyla; bulunamayan id'ler haritada yok. */
	public Map<Long, StageExtensionTypeSummary> extensionTypesById(List<Long> ids) {
		return extensionTypes.typesById(ids);
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, StageExtensionTypeFieldResponse> extensionTypeFieldsById(List<Long> fieldIds) {
		return extensionTypes.fieldsById(fieldIds);
	}

}
