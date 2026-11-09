package com.olivamore.api.process.stageextensiontype;

import com.olivamore.api.process.StageExtensionTypeFieldResponse;
import com.olivamore.api.process.StageExtensionTypeSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entity'den API cevabina. */
@Component
public class StageExtensionTypeResponses {

	private final StageExtensionTypeRepository types;
	private final StageExtensionTypeFieldRepository fields;

	StageExtensionTypeResponses(StageExtensionTypeRepository types, StageExtensionTypeFieldRepository fields) {
		this.types = types;
		this.fields = fields;
	}

	@Transactional(readOnly = true)
	List<StageExtensionTypeResponse> all() {
		List<StageExtensionType> all = types.findAllByOrderByNameAsc();
		Map<Long, List<StageExtensionTypeFieldResponse>> byType =
			fieldsByType(all.stream().map(StageExtensionType::getId).toList());
		return all.stream().map(type -> new StageExtensionTypeResponse(type.getId(), type.getKey(), type.getName(),
			type.getDescription(), byType.getOrDefault(type.getId(), List.of()), type.getCreatedAt(),
			type.getUpdatedAt())).toList();
	}

	/** Alanlariyla; bulunamayan id'ler haritada yok. */
	@Transactional(readOnly = true)
	public Map<Long, StageExtensionTypeSummary> typesById(List<Long> ids) {
		List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
		Map<Long, List<StageExtensionTypeFieldResponse>> byType = fieldsByType(present);
		return types.findAllById(present).stream()
			.map(type -> new StageExtensionTypeSummary(type.getId(), type.getKey(), type.getName(),
				type.getDescription(), byType.getOrDefault(type.getId(), List.of())))
			.collect(Collectors.toMap(StageExtensionTypeSummary::id, Function.identity()));
	}

	/** Uzanti tipine gore, sort_order sirasinda. */
	@Transactional(readOnly = true)
	public Map<Long, List<StageExtensionTypeFieldResponse>> fieldsByType(List<Long> typeIds) {
		return fields.findByStageExtensionTypeIdInOrderBySortOrderAscIdAsc(typeIds).stream()
			.map(StageExtensionTypeResponses::of)
			.collect(Collectors.groupingBy(StageExtensionTypeFieldResponse::stageExtensionTypeId));
	}

	/** Bulunamayan id'ler haritada yok. */
	@Transactional(readOnly = true)
	public Map<Long, StageExtensionTypeFieldResponse> fieldsById(List<Long> fieldIds) {
		List<Long> present = fieldIds.stream().filter(Objects::nonNull).distinct().toList();
		return fields.findAllById(present).stream()
			.map(StageExtensionTypeResponses::of)
			.collect(Collectors.toMap(StageExtensionTypeFieldResponse::id, Function.identity()));
	}

	private static StageExtensionTypeFieldResponse of(StageExtensionTypeField field) {
		return new StageExtensionTypeFieldResponse(field.getId(), field.getStageExtensionTypeId(),
			field.getFieldKey(), field.getLabel(), field.getDataType(), field.isRequired(), field.getSortOrder(),
			field.getDefaultValueBoolean(), field.getDefaultValueInteger(), field.getDefaultValueDecimal(),
			field.getDefaultValueText(), field.getDefaultValueJson(), field.getOptionsJson(),
			field.getDescription());
	}

}
