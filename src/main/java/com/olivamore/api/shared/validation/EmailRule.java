package com.olivamore.api.shared.validation;

import java.util.regex.Pattern;

/**
 * Laravel'in varsayilan {@code email} kurali (egulias/email-validator, RFCValidation)
 * icin yakinsama. Laravel'de gozlenen:
 * <ul>
 *   <li>{@code abc} gecersiz, {@code a@b} gecerli (alan adinda nokta sart degil)</li>
 *   <li>uzunluk sinirlari uygulanmiyor (250 karakterlik yerel kisim gecti)</li>
 * </ul>
 * Tirnakli yerel kisim ({@code "a b"@c.d}) ve yorum ({@code a(x)@b}) gibi nadir RFC bicimleri
 * desteklenmez (bkz. DEVIATIONS.md).
 */
final class EmailRule {

	/** RFC 5322 atext + UTF-8 (egulias RFCValidation UTF-8'i kabul ediyor) */
	private static final String ATEXT = "[\\p{L}\\p{N}!#$%&'*+/=?^_`{|}~-]";
	private static final Pattern LOCAL = Pattern.compile(ATEXT + "+(\\." + ATEXT + "+)*");
	private static final Pattern LABEL = Pattern.compile("[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?");
	private static final Pattern IP_LITERAL = Pattern.compile("\\[(IPv6:[0-9A-Fa-f:.]+|[0-9]{1,3}(\\.[0-9]{1,3}){3})]");

	private EmailRule() {
	}

	static boolean isValid(String email) {
		int at = email.lastIndexOf('@');
		if (at <= 0 || at == email.length() - 1) {
			return false;
		}
		String local = email.substring(0, at);
		String domain = email.substring(at + 1);
		if (!LOCAL.matcher(local).matches()) {
			return false;
		}
		if (domain.startsWith("[")) {
			return IP_LITERAL.matcher(domain).matches();
		}
		for (String label : domain.split("\\.", -1)) {
			if (!LABEL.matcher(label).matches()) {
				return false;
			}
		}
		return true;
	}

}
