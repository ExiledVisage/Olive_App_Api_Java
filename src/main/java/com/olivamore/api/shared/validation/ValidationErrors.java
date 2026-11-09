package com.olivamore.api.shared.validation;

import com.olivamore.api.shared.error.LaravelValidationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bean Validation ile ifade edilemeyen liste kontrollerini toplar: tekrar eden ve bulunmayan id'ler. */
public final class ValidationErrors {

	private final Map<String, List<String>> errors = new LinkedHashMap<>();

	public void add(String field, String template) {
		errors.computeIfAbsent(field, key -> new ArrayList<>()).add(LaravelMessages.format(template, field));
	}

	/** @param key eleman nesneyse alan adi, duz id listesiyse {@code null} */
	public void distinct(String list, String key, List<Long> values) {
		for (int i = 0; i < values.size(); i++) {
			Long value = values.get(i);
			if (value != null && values.stream().filter(value::equals).count() > 1) {
				add(path(list, i, key), LaravelMessages.DISTINCT);
			}
		}
	}

	/** @param key eleman nesneyse alan adi, duz id listesiyse {@code null} */
	public void exists(String list, String key, List<Long> values, Collection<Long> existing) {
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i) != null && !existing.contains(values.get(i))) {
				add(path(list, i, key), LaravelMessages.EXISTS);
			}
		}
	}

	public void throwIfAny() {
		if (!errors.isEmpty()) {
			throw new LaravelValidationException(errors);
		}
	}

	/** Bos olmayan, tekrarsiz id'ler. */
	public static List<Long> present(Collection<Long> ids) {
		return ids.stream().filter(Objects::nonNull).distinct().toList();
	}

	private static String path(String list, int index, String key) {
		return list + "." + index + (key == null ? "" : "." + key);
	}

}
