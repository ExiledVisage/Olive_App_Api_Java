package com.olivamore.api.shared.php;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PHP/Carbon tarih donusumlerinin API'de gecen bicimleri.
 * <ul>
 *   <li>{@link #parse}: Eloquent {@code datetime} cast'inin yazarken yaptigi ({@code asDateTime}): "Y-m-d" gun
 *       basi, digerleri Carbon::parse; saat dilimi verilmisse <b>cevrilmez</b>, o dilimdeki saat yazilir
 *       ("10:00+03:00" -> 10:00). Saniye alti atilir.</li>
 *   <li>{@link #matchesFormat}: {@code date_format} kurali (createFromFormat + ayni bicime geri yazinca ayni metin)</li>
 * </ul>
 * Laravel'in {@code date} kurali PHP'nin strtotime'ini kullanir ("next monday" bile gecer); Java yalnizca ISO
 * bicimlerini kabul eder (DEVIATIONS: tarih dogrulamasi yakinsamasi).
 */
public final class PhpDates {

	private static final Pattern ISO = Pattern.compile(
		"^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ](\\d{1,2}):(\\d{2})(?::(\\d{2})(?:\\.\\d+)?)?)?\\s*(Z|[+-]\\d{2}:?\\d{2})?$");

	private PhpDates() {
	}

	/** Eloquent datetime cast'inin yazdigi deger; anlasilmayan metin icin bos. */
	public static Optional<LocalDateTime> parse(String value) {
		if (value == null) {
			return Optional.empty();
		}
		Matcher m = ISO.matcher(value.strip());
		if (!m.matches()) {
			return Optional.empty();
		}
		try {
			LocalDate date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
			if (m.group(4) == null) {
				return Optional.of(date.atStartOfDay());
			}
			LocalTime time = LocalTime.of(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
				m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
			return Optional.of(date.atTime(time));
		}
		catch (DateTimeException e) {
			return Optional.empty();
		}
	}

	/** {@code date_format:F}: desteklenen bicimler Y-m-d, Y-m-d H:i:s, Y-m-d H:i, H:i. */
	public static boolean matchesFormat(String value, String phpFormat) {
		DateTimeFormatter formatter = formatter(phpFormat);
		try {
			return value != null && formatter.format(formatter.parse(value)).equals(value);
		}
		catch (DateTimeException e) {
			return false;
		}
	}

	/** {@code DateTime::createFromFormat('!'.F, $value)}; bicime uymayan icin bos. */
	public static Optional<LocalDateTime> fromFormat(String value, String phpFormat) {
		if (value == null || !matchesFormat(value, phpFormat)) {
			return Optional.empty();
		}
		var parsed = formatter(phpFormat).parse(value);
		LocalDate date = parsed.isSupported(java.time.temporal.ChronoField.EPOCH_DAY) ? LocalDate.from(parsed) : LocalDate.of(1970, 1, 1);
		LocalTime time = parsed.isSupported(java.time.temporal.ChronoField.HOUR_OF_DAY) ? LocalTime.from(parsed) : LocalTime.MIDNIGHT;
		return Optional.of(date.atTime(time));
	}

	private static DateTimeFormatter formatter(String phpFormat) {
		String pattern = switch (phpFormat) {
			case "Y-m-d" -> "uuuu-MM-dd";
			case "Y-m-d H:i:s" -> "uuuu-MM-dd HH:mm:ss";
			case "Y-m-d H:i" -> "uuuu-MM-dd HH:mm";
			case "H:i" -> "HH:mm";
			default -> throw new IllegalArgumentException("Desteklenmeyen date_format: " + phpFormat);
		};
		return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
	}

}
