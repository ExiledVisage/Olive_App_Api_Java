package com.olivamore.api.process;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Proses tipinin API cevabi. {@code fields}, proses alanlari da taşınana kadar {@code Map}. */
public record ProcessTypeResponse(
	Long id,
	String name,
	String description,
	ProcessCategory category,
	boolean isActive,
	List<Map<String, Object>> fields,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {
}
