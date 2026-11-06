package com.olivamore.api.shared.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bir is kuralinin ihlal edildigini bildirir. Laravel'deki
 * {@code App\Exceptions\BusinessRuleException} karsiligi.
 * <p>
 * Servis katmani kullaniciya gosterilebilir her reddetme icin bunu firlatir
 * (tank kapasitesi yetmiyor, materyal bu tankta degil...). Cevap 422'dir:
 * <pre>{"message": "...", "errors": {}}</pre>
 * {@code errors} bossa Laravel {@code []} degil {@code {}} yazar.
 * <p>
 * Programlama hatalari bununla sarilmamali; onlar 500 olarak kalmali.
 */
public class BusinessRuleException extends RuntimeException {

	private final Map<String, List<String>> errors;

	public BusinessRuleException(String message) {
		this(message, Map.of());
	}

	public BusinessRuleException(String message, Map<String, List<String>> errors) {
		super(message);
		this.errors = new LinkedHashMap<>(errors);
	}

	public static BusinessRuleException withErrors(String message, Map<String, List<String>> errors) {
		return new BusinessRuleException(message, errors);
	}

	public Map<String, List<String>> errors() {
		return errors;
	}

}
