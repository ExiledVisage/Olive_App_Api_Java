package com.olivamore.api.process;

import java.util.List;

/** Asama uzantisinin icinde gosterilen uzanti tipi, alanlariyla. */
public record StageExtensionTypeSummary(
	Long id,
	String key,
	String name,
	String description,
	List<StageExtensionTypeFieldResponse> fields) {
}
