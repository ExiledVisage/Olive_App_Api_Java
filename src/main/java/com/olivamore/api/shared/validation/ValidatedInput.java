package com.olivamore.api.shared.validation;

import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Laravel'in {@code $request->validated()}'i: yalnizca kurali olan ve istekte bulunan alanlar.
 * {@link #has(String)} PHP'nin {@code array_key_exists}'i gibi: alan null olsa da true.
 */
public final class ValidatedInput {

	private final Map<String, Object> data;
	private final JsonMapper json;

	ValidatedInput(Map<String, Object> data, JsonMapper json) {
		this.data = Collections.unmodifiableMap(data);
		this.json = json;
	}

	public Map<String, Object> all() {
		return data;
	}

	public boolean has(String key) {
		return data.containsKey(key);
	}

	public Object get(String key) {
		return data.get(key);
	}

	public String string(String key) {
		Object value = data.get(key);
		return value == null ? null : LaravelValidator.phpString(value);
	}

	@SuppressWarnings("unchecked")
	public List<String> strings(String key) {
		Object value = data.get(key);
		if (value == null) {
			return null;
		}
		return ((List<Object>) value).stream().map(LaravelValidator::phpString).toList();
	}

	/** Dogrulanmis veriyi bir record'a cevirir (snake_case alan adlari). */
	public <T> T as(Class<T> type) {
		return json.convertValue(data, type);
	}

}
