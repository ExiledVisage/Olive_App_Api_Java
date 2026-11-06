package com.olivamore.api.shared.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Laravel'in her istekte calisan iki middleware'inin JSON govdesi icin karsiligi:
 * <ul>
 *   <li>{@code TrimStrings}: metinlerin basi ve sonundaki bosluklar silinir
 *       (sifre alanlari haric)</li>
 *   <li>{@code ConvertEmptyStringsToNull}: bos metin {@code null} olur</li>
 * </ul>
 * Metin beklenen yere sayi ya da boolean gelirse kabul edilmez: Laravel'in
 * {@code string} kurali da reddeder ("The x field must be a string.").
 */
public class LaravelStringDeserializer extends ValueDeserializer<String> {

	/** Laravel'in TrimStrings::$except listesi. */
	public static final Set<String> NOT_TRIMMED = Set.of("current_password", "password", "password_confirmation");

	/** TrimStrings'in kullandigi desen: Unicode bosluklar + BOM, zero-width space, LRM. */
	private static final Pattern TRIM = Pattern.compile("^[\\s\\x{FEFF}\\x{200B}\\x{200E}]+|[\\s\\x{FEFF}\\x{200B}\\x{200E}]+$",
		Pattern.UNICODE_CHARACTER_CLASS);

	@Override
	public String deserialize(JsonParser p, DeserializationContext ctxt) {
		if (p.currentToken() != JsonToken.VALUE_STRING) {
			return (String) ctxt.handleUnexpectedToken(String.class, p);
		}
		String field = p.currentName();
		return normalize(p.getString(), field != null && NOT_TRIMMED.contains(field));
	}

	public static String normalize(String value, boolean keepWhitespace) {
		if (value == null) {
			return null;
		}
		String result = keepWhitespace ? value : TRIM.matcher(value).replaceAll("");
		return result.isEmpty() ? null : result;
	}

}
