package com.olivamore.api.shared.validation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bir alanin dogrulama hatasi. {@code message} bir anahtar ({@link MessageKeys}) ya da duz metindir; anahtar hata
 * cevabi yazilirken {@code messages.properties}'ten cozulur ve {@code args} ile doldurulur.
 *
 * @param field JSON yolu, ornek {@code items.0.quantity}
 */
public record Violation(String field, String message, Map<String, Object> args) {

	public Violation {
		args = Map.copyOf(args);
	}

	public static Violation of(String field, String message) {
		return new Violation(field, message, Map.of());
	}

	/** @param nameValues degisken adi ve degeri, sirayla: {@code "max", 100} */
	public static Violation of(String field, String message, Object... nameValues) {
		Map<String, Object> args = new LinkedHashMap<>();
		for (int i = 0; i < nameValues.length; i += 2) {
			args.put(String.valueOf(nameValues[i]), nameValues[i + 1]);
		}
		return new Violation(field, message, args);
	}

}
