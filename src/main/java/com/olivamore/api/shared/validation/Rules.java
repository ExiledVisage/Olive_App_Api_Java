package com.olivamore.api.shared.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bir FormRequest'in {@code rules()} dizisi, ayni sirayla (hata sirasi buna bagli).
 * <pre>
 * Rules.of()
 *     .field("name", "required|string|max:255")
 *     .field("email", "required|email|max:255|unique:users,email," + userId)
 * </pre>
 */
public final class Rules {

	private final Map<String, List<LaravelValidator.ParsedRule>> fields = new LinkedHashMap<>();

	private Rules() {
	}

	public static Rules of() {
		return new Rules();
	}

	public Rules field(String attribute, String rules) {
		fields.put(attribute, LaravelValidator.ParsedRule.parse(rules));
		return this;
	}

	Map<String, List<LaravelValidator.ParsedRule>> fields() {
		return Collections.unmodifiableMap(fields);
	}

}
