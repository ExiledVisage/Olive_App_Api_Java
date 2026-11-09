package com.olivamore.api.process;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** Proses alaninin API cevabi. */
public record ProcessTypeFieldResponse(
	Long id,
	Long processTypeId,
	String fieldKey,
	String label,
	FieldDataType dataType,
	boolean isRequired,
	String unit,
	int sortOrder,
	boolean isActive,
	JsonNode options,
	String defaultValue,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {
}
