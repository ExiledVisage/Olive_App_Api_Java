package com.olivamore.api.shared.error;

import com.olivamore.api.shared.validation.LaravelMessages;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel'in {@code ValidationException} karsiligi (422). Bean Validation ile
 * ifade edilemeyen kontroller (query parametreleri, birden fazla alana bakan
 * kurallar) icin elle firlatilir.
 * <pre>{"message": "The email field is required. (and 1 more error)",
 *  "errors": {"email": ["..."], "password": ["..."]}}</pre>
 * Alanlarin sirasi korunur: mesajdaki ilk hata, ilk eklenen alanin ilk hatasidir.
 */
public class LaravelValidationException extends RuntimeException {

	private final Map<String, List<String>> errors;

	public LaravelValidationException(Map<String, List<String>> errors) {
		super(LaravelMessages.summary(errors));
		this.errors = new LinkedHashMap<>(errors);
	}

	public static LaravelValidationException of(String field, String message) {
		return new LaravelValidationException(Map.of(field, List.of(message)));
	}

	public Map<String, List<String>> errors() {
		return errors;
	}

}
