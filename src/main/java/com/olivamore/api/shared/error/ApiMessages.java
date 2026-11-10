package com.olivamore.api.shared.error;

import com.olivamore.api.shared.validation.Violation;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hata cevaplarinin metinleri. {@code {validation.required}} bicimindeki mesajlar {@code messages.properties}'ten
 * okunur, duz metinler oldugu gibi kalir; ikisinde de {@code {ad}} degiskenleri doldurulur.
 */
@Component
public class ApiMessages {

	private static final Pattern KEY = Pattern.compile("^\\{([\\w.-]+)}$");
	private static final Pattern PARAMETER = Pattern.compile("\\{(\\w+)}");

	private final MessageSource source;

	public ApiMessages(MessageSource source) {
		this.source = source;
	}

	/** @param message anahtar ({@code {error.unauthenticated}}) ya da duz metin */
	public String text(String message, Map<String, ?> args) {
		Matcher key = KEY.matcher(message);
		String template = key.matches()
			? source.getMessage(key.group(1), null, message, LocaleContextHolder.getLocale())
			: message;
		return fill(template, args);
	}

	public String text(String message) {
		return text(message, Map.of());
	}

	/** Alan hatasi: {@code {attribute}} alanin okunur adi olur (mesaj degiskenleriyle ezilebilir). */
	public String text(Violation violation) {
		Map<String, Object> args = new HashMap<>();
		args.put("attribute", attribute(violation.field()));
		args.putAll(violation.args());
		return text(violation.message(), args);
	}

	/**
	 * Alanin mesajdaki adi: {@code per_page -> "per page"}. Indisli yollar oldugu gibi kalir:
	 * {@code items.0.quantity}.
	 */
	public static String attribute(String field) {
		return field.matches(".*\\.\\d+(\\..*)?") ? field : field.replace('_', ' ');
	}

	private static String fill(String template, Map<String, ?> args) {
		if (args.isEmpty() || template == null) {
			return template;
		}
		Matcher m = PARAMETER.matcher(template);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			Object value = args.get(m.group(1));
			m.appendReplacement(out, Matcher.quoteReplacement(value == null && !args.containsKey(m.group(1))
				? m.group() : String.valueOf(value)));
		}
		return m.appendTail(out).toString();
	}

}
