package com.olivamore.api.machine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

record MachineTypeResponse(
	Long id,
	String key,
	String name,
	String description,
	int sortOrder,
	boolean isActive,
	long machineCount,
	List<Field> fields,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Field(Long id, Long machineTypeId, String fieldKey, String label, String dataType, String scope, String unit,
		boolean isRequired, int sortOrder, BigDecimal minValue, BigDecimal maxValue, boolean isActive) {

		static Field of(MachineTypeField field) {
			return new Field(field.getId(), field.getMachineType().getId(), field.getFieldKey(), field.getLabel(),
				field.getDataType(), field.getScope(), field.getUnit(), field.isRequired(), field.getSortOrder(), field.getMinValue(),
				field.getMaxValue(), field.isActive());
		}

	}

	static MachineTypeResponse of(MachineType type, long machineCount) {
		return new MachineTypeResponse(type.getId(), type.getKey(), type.getName(), type.getDescription(),
			type.getSortOrder(), type.isActive(), machineCount, type.getFields().stream().map(Field::of).toList(),
			type.getCreatedAt(), type.getUpdatedAt());
	}

}
