package com.olivamore.api.shared.web;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Laravel validation'in dayandigi PHP sayi kontrolleri, birebir. */
public final class PhpNumbers {

	/** {@code filter_var($v, FILTER_VALIDATE_INT)}: isaret olabilir, basta sifir olamaz ("05" gecersiz). */
	private static final Pattern INT = Pattern.compile("^[+-]?(0|[1-9][0-9]*)$");

	/** {@code is_numeric($v)}: ondalik ve us kabul eder ("1.5", ".5", "1e3", "+5"). */
	private static final Pattern NUMERIC = Pattern.compile("^\\s*[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?\\s*$");

	private PhpNumbers() {
	}

	public static OptionalLong validateInt(String value) {
		if (value == null) {
			return OptionalLong.empty();
		}
		String trimmed = value.strip();
		if (!INT.matcher(trimmed).matches()) {
			return OptionalLong.empty();
		}
		try {
			return OptionalLong.of(Long.parseLong(trimmed));
		}
		catch (NumberFormatException overflow) {
			return OptionalLong.empty();
		}
	}

	public static OptionalDouble numeric(String value) {
		if (value == null || !NUMERIC.matcher(value).matches()) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(Double.parseDouble(value.strip()));
	}

	/**
	 * Laravel'in {@code min}/{@code max} kurallarinin olctugu buyukluk
	 * ({@code Validator::getSize}) — alanda numeric/integer kurali varken:
	 * sayiysa degeri, degilse karakter sayisi (null icin 0).
	 */
	public static double size(String value) {
		OptionalDouble number = numeric(value);
		if (number.isPresent()) {
			return number.getAsDouble();
		}
		return value == null ? 0 : value.codePointCount(0, value.length());
	}

}
