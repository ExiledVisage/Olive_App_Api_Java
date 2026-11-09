package com.olivamore.api.process.stageextensiontype;

import com.olivamore.api.process.StageExtensionTypeFieldResponse;

import java.time.LocalDateTime;
import java.util.List;

record StageExtensionTypeResponse(
	Long id,
	String key,
	String name,
	String description,
	List<StageExtensionTypeFieldResponse> fields,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {
}
