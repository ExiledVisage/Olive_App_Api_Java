package com.olivamore.api.process;

import com.olivamore.api.process.processtype.ProcessTypeResponses;
import com.olivamore.api.process.processtypefield.ProcessTypeFieldResponses;
import com.olivamore.api.process.stageextensiontype.StageExtensionTypeResponses;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Proses cevaplarinin diger modullere acik kismi: uretim hattinin asamalarindaki proses tipleri,
 * parti proseslerindeki alan tanimlari ve uzanti tipi alanlari.
 */
@Component
public class ProcessResources {

	private final ProcessTypeResponses types;
	private final ProcessTypeFieldResponses fields;
	private final StageExtensionTypeResponses extensionTypes;
	private final JdbcClient jdbc;

	ProcessResources(ProcessTypeResponses types, ProcessTypeFieldResponses fields,
		StageExtensionTypeResponses extensionTypes, JdbcClient jdbc) {
		this.types = types;
		this.fields = fields;
		this.extensionTypes = extensionTypes;
		this.jdbc = jdbc;
	}

	/** Proses alani (kolonlar ham haliyle; {@code dataType} tanimli metin). */
	public record Field(Long id, Long processTypeId, String fieldKey, String label, String dataType,
		boolean isRequired, String unit, Integer sortOrder, boolean isActive) {
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, Field> fields(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select * from process_type_fields where id in (:ids)").param("ids", present)
			.query(Field.class).list().stream().collect(Collectors.toMap(Field::id, Function.identity()));
	}

	/** Proses tipinin alanlari, sort_order sirasinda. */
	public List<Field> fieldsOfType(long processTypeId) {
		return jdbc.sql("select * from process_type_fields where process_type_id = :type order by sort_order asc")
			.param("type", processTypeId).query(Field.class).list();
	}

	/** Bu kategorideki proses tipleri. */
	public Set<Long> processTypeIdsInCategory(String category) {
		return Set.copyOf(jdbc.sql("select id from process_types where category = :category")
			.param("category", category).query(Long.class).list());
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
