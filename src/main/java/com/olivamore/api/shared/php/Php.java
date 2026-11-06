package com.olivamore.api.shared.php;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Laravel kodunda gecen PHP donusumleri, birebir. Degerler {@code json_decode(..., true)}
 * karsiligi Java tipleridir (Long, BigDecimal, String, Boolean, Map, List, null).
 */
public final class Php {

	/** {@code (int) "7abc"}: bastaki bosluklar atlanir, bastaki sayisal kisim alinir, kalan yok sayilir. */
	private static final Pattern LEADING_NUMBER = Pattern.compile("^[ \\t\\n\\r\\x0B\\f]*([+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?)");

	private Php() {
	}

	/** {@code (int) $value} */
	public static long intval(Object value) {
		return switch (value) {
			case null -> 0;
			case Long l -> l;
			case Integer i -> i;
			case BigInteger b -> b.bitLength() < 64 ? b.longValue() : (b.signum() > 0 ? Long.MAX_VALUE : Long.MIN_VALUE);
			case BigDecimal d -> truncate(d);
			case Double d -> d.isNaN() || d.isInfinite() ? 0 : truncate(BigDecimal.valueOf(d));
			case Boolean b -> b ? 1 : 0;
			case String s -> {
				Matcher m = LEADING_NUMBER.matcher(s);
				yield m.find() ? truncate(new BigDecimal(m.group(1).startsWith("+") ? m.group(1).substring(1) : m.group(1)))
					: 0;
			}
			case Collection<?> c -> c.isEmpty() ? 0 : 1;
			case Map<?, ?> m -> m.isEmpty() ? 0 : 1;
			default -> 0;
		};
	}

	/** {@code (string) $value} (diziler PHP'de "Array" olur). */
	public static String strval(Object value) {
		return switch (value) {
			case null -> "";
			case Boolean b -> b ? "1" : "";
			case Double d -> floatString(d);
			case Float f -> floatString(f.doubleValue());
			case BigDecimal d -> d.stripTrailingZeros().scale() <= 0 ? d.toBigInteger().toString() : d.stripTrailingZeros().toPlainString();
			case Collection<?> c -> "Array";
			case Map<?, ?> m -> "Array";
			default -> value.toString();
		};
	}

	/** {@code (float) $value}: sayilar oldugu gibi, metinde bastaki sayisal kisim (" 3.5" -> 3.5, "1e3x" -> 1000). */
	public static double floatval(Object value) {
		return switch (value) {
			case null -> 0;
			case Number n -> n.doubleValue();
			case Boolean b -> b ? 1 : 0;
			case String s -> {
				Matcher m = LEADING_NUMBER.matcher(s);
				yield m.find() ? Double.parseDouble(m.group(1)) : 0;
			}
			case Collection<?> c -> c.isEmpty() ? 0 : 1;
			case Map<?, ?> m -> m.isEmpty() ? 0 : 1;
			default -> 0;
		};
	}

	/**
	 * PHP'nin float -> metin donusumu ({@code precision=14}, {@code %.14G}): veritabanina PDO ile
	 * yazilan deger budur. {@code 3200 * 0.915 -> "2928"}, {@code 1/3 -> "0.33333333333333"},
	 * {@code 1e25 -> "1.0E+25"}. (JSON ciktisi ise en kisa gosterim: {@link #jsonFloat}.)
	 */
	public static String floatString(double value) {
		if (Double.isNaN(value)) {
			return "NAN";
		}
		if (Double.isInfinite(value)) {
			return value > 0 ? "INF" : "-INF";
		}
		if (value == 0) {
			return (1 / value < 0) ? "-0" : "0";
		}
		BigDecimal rounded = new BigDecimal(value).round(new java.math.MathContext(14, java.math.RoundingMode.HALF_EVEN));
		int exponent = rounded.precision() - rounded.scale() - 1;
		if (exponent < -4 || exponent >= 14) {
			BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
			String digits = mantissa.toPlainString();
			if (!digits.contains(".")) {
				digits += ".0";
			}
			return digits + "E" + (exponent < 0 ? "-" : "+") + Math.abs(exponent);
		}
		String plain = rounded.stripTrailingZeros().toPlainString();
		return plain;
	}

	/**
	 * PHP 8.4 {@code round($value, $places)} (yarim deger sifirdan uzaga). PHP 8.4, ikili gosterimin
	 * hatasini duzeltip ondalik gosterimi yuvarlar: {@code round(0.285, 2) = 0.29}. Esdegeri: en kisa
	 * ondalik gosterim (Double.toString) uzerinden HALF_UP.
	 */
	public static double round(double value, int places) {
		if (!Double.isFinite(value) || value == 0) {
			return value;
		}
		return new BigDecimal(Double.toString(value)).setScale(places, java.math.RoundingMode.HALF_UP).doubleValue();
	}

	/** PHP json_encode float'u (serialize_precision=-1): en kisa gosterim, tam sayiya esitse ".0" yok. */
	public static Number jsonFloat(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return (long) value;
		}
		return value;
	}

	/** {@code basename()} (Linux PHP'si: yalnizca "/" ayirici; sondaki "/" atilir). */
	public static String basename(String path) {
		String trimmed = path;
		while (trimmed.length() > 1 && trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		int slash = trimmed.lastIndexOf('/');
		return slash < 0 ? trimmed : trimmed.substring(slash + 1);
	}

	/**
	 * Laravel {@code Str::ascii} (voku/portable-ascii) yakinsamasi: aksanlar kaldirilir, Turkce ve
	 * birkac Latin harf karsiligina cevrilir, ASCII disinda kalan karakter atilir.
	 */
	public static String ascii(String value) {
		StringBuilder pre = new StringBuilder();
		for (char c : value.toCharArray()) {
			pre.append(switch (c) {
				case 'ı' -> "i";
				case 'İ' -> "I";
				case 'ß' -> "ss";
				case 'æ' -> "ae";
				case 'Æ' -> "AE";
				case 'ø' -> "o";
				case 'Ø' -> "O";
				case 'ł' -> "l";
				case 'Ł' -> "L";
				case 'đ' -> "d";
				case 'Đ' -> "D";
				default -> String.valueOf(c);
			});
		}
		String decomposed = Normalizer.normalize(pre, Normalizer.Form.NFD);
		StringBuilder out = new StringBuilder();
		decomposed.codePoints().filter(cp -> cp < 0x80).forEach(out::appendCodePoint);
		return out.toString();
	}

	private static long truncate(BigDecimal value) {
		BigInteger integer = value.toBigInteger();
		return integer.bitLength() < 64 ? integer.longValue() : (integer.signum() > 0 ? Long.MAX_VALUE : Long.MIN_VALUE);
	}

}
