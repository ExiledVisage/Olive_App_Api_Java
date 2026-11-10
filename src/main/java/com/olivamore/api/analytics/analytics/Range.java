package com.olivamore.api.analytics.analytics;

import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

/** Rapor araligi: {@code from} gun basi, {@code to} gun sonu; en fazla iki yil. */
public record Range(LocalDate fromDate, LocalDate toDate) {

	private static final Pattern YEAR_MONTH_DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private static final long MAX_DAYS_BETWEEN = 730;

	/** Y-m-d, to >= from; aralik iki yili gecerse 422. */
	static Range of(String from, String to) {
		ValidationErrors errors = new ValidationErrors();
		Optional<LocalDate> fromDate = ymd(from);
		Optional<LocalDate> toDate = ymd(to);
		if (from == null) {
			errors.add("from", MessageKeys.REQUIRED);
		}
		else if (fromDate.isEmpty()) {
			errors.add("from", MessageKeys.DATE_FORMAT, "format", "Y-m-d");
		}
		if (to == null) {
			errors.add("to", MessageKeys.REQUIRED);
		}
		else {
			if (toDate.isEmpty()) {
				errors.add("to", MessageKeys.DATE_FORMAT, "format", "Y-m-d");
			}
			if (!inOrder(from, to)) {
				errors.add("to", MessageKeys.AFTER_OR_EQUAL, "other", "from");
			}
		}
		errors.throwIfAny();
		Range range = new Range(fromDate.orElseThrow(), toDate.orElseThrow());
		if (range.inclusiveDays() - 1 > MAX_DAYS_BETWEEN) {
			throw new HttpStatusException(422, "Aralık en fazla iki yıl olabilir.");
		}
		return range;
	}

	/** Gecerli bir Y-m-d tarihi. */
	public static Optional<LocalDate> ymd(String text) {
		if (text == null || !YEAR_MONTH_DAY.matcher(text).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(LocalDate.parse(text));
		}
		catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	/** Iki deger de gecerli tarih ve {@code to}, {@code from}'dan once degil. */
	public static boolean inOrder(String from, String to) {
		Optional<LocalDate> start = ymd(from);
		Optional<LocalDate> end = ymd(to);
		return start.isPresent() && end.isPresent() && !end.get().isBefore(start.get());
	}

	LocalDateTime from() {
		return fromDate.atStartOfDay();
	}

	LocalDateTime to() {
		return toDate.atTime(23, 59, 59);
	}

	/** Iki uc dahil gun sayisi. */
	long inclusiveDays() {
		return ChronoUnit.DAYS.between(fromDate, toDate) + 1;
	}

}
