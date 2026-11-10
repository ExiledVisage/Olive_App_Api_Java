package com.olivamore.api.shared.error;

import com.olivamore.api.shared.validation.Violation;

import java.util.List;

/**
 * Istek dogrulanamadi (422). Bean Validation ile ifade edilemeyen kontroller (birden fazla alana bakan kurallar,
 * veritabanindaki varlik ve benzersizlik) icin servisler firlatir. Hatalarin sirasi korunur.
 */
public class ValidationFailedException extends RuntimeException {

	private final List<Violation> violations;

	public ValidationFailedException(List<Violation> violations) {
		super(violations.isEmpty() ? "Validation failed" : "Validation failed: " + violations.getFirst().field());
		this.violations = List.copyOf(violations);
	}

	/** @param message {@code MessageKeys} anahtari ya da duz metin */
	public static ValidationFailedException of(String field, String message) {
		return new ValidationFailedException(List.of(Violation.of(field, message)));
	}

	/** @param nameValues mesaj degiskenleri, ad ve deger sirayla */
	public static ValidationFailedException of(String field, String message, Object... nameValues) {
		return new ValidationFailedException(List.of(Violation.of(field, message, nameValues)));
	}

	public List<Violation> violations() {
		return violations;
	}

}
