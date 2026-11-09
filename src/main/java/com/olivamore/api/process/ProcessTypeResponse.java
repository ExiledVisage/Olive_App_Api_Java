package com.olivamore.api.process;

import java.time.LocalDateTime;
import java.util.List;

/** Proses tipinin API cevabi. */
public record ProcessTypeResponse(
	Long id,
	String name,
	String description,
	ProcessCategory category,
	boolean isActive,
	List<ProcessTypeFieldResponse> fields,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {
}
