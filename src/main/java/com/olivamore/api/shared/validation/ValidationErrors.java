package com.olivamore.api.shared.validation;

import com.olivamore.api.shared.error.ValidationFailedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Bean Validation ile ifade edilemeyen kontrollerin hatalarini toplar: tekrar eden ve bulunmayan id'ler vb. */
public final class ValidationErrors {

	private final List<Violation> violations = new ArrayList<>();

	/** @param message {@link MessageKeys} anahtari ya da duz metin */
	public void add(String field, String message) {
		violations.add(Violation.of(field, message));
	}

	/** @param nameValues mesaj degiskenleri, ad ve deger sirayla */
	public void add(String field, String message, Object... nameValues) {
		violations.add(Violation.of(field, message, nameValues));
	}

	/** @param key eleman nesneyse alan adi, duz id listesiyse {@code null} */
	public void distinct(String list, String key, List<?> values) {
		for (int i = 0; i < values.size(); i++) {
			Object value = values.get(i);
			if (value != null && values.stream().filter(value::equals).count() > 1) {
				add(path(list, i, key), MessageKeys.DISTINCT);
			}
		}
	}

	/** @param key eleman nesneyse alan adi, duz id listesiyse {@code null} */
	public void exists(String list, String key, List<?> values, Collection<?> existing) {
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i) != null && !existing.contains(values.get(i))) {
				add(path(list, i, key), MessageKeys.EXISTS);
			}
		}
	}

	/** @param key eleman nesneyse alan adi, duz listeyse {@code null} */
	public void unique(String list, String key, List<?> values, Collection<?> taken) {
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i) != null && taken.contains(values.get(i))) {
				add(path(list, i, key), MessageKeys.UNIQUE);
			}
		}
	}

	public boolean isEmpty() {
		return violations.isEmpty();
	}

	public void throwIfAny() {
		if (!violations.isEmpty()) {
			throw new ValidationFailedException(violations);
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
