package com.olivamore.api.shared.persistence;

import com.olivamore.api.shared.php.Php;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Eloquent {@code $casts} karsiliklari (resource'larda uygulanir). */
public final class Casts {

	private Casts() {
	}

	/** {@code decimal:N}: N basamakli metin, HALF_UP ("1000" -> "1000.00"); null kalir. */
	public static String decimal(Object value, int scale) {
		BigDecimal d = toDecimal(value);
		return d == null ? null : d.setScale(scale, RoundingMode.HALF_UP).toPlainString();
	}

	/** {@code boolean}: null kalir, digerleri PHP (bool). */
	public static Boolean bool(Object value) {
		return switch (value) {
			case null -> null;
			case Boolean b -> b;
			case String s -> !s.isEmpty() && !s.equals("0");
			case Number n -> new BigDecimal(n.toString()).signum() != 0;
			default -> true;
		};
	}

	/** {@code integer}: null kalir, digerleri (int). */
	public static Long integer(Object value) {
		return value == null ? null : Php.intval(value);
	}

	/** Deger istekten de gelebilir (sayi ya da metin); sayi olmayan metin Laravel'de de hata (brick/math). */
	public static BigDecimal toDecimal(Object value) {
		return switch (value) {
			case null -> null;
			case BigDecimal d -> d;
			case Number n -> new BigDecimal(n.toString());
			case Boolean b -> b ? BigDecimal.ONE : BigDecimal.ZERO;
			default -> new BigDecimal(value.toString().strip());
		};
	}

}
