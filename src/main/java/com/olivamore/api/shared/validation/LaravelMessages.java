package com.olivamore.api.shared.validation;

import java.util.List;
import java.util.Map;

/**
 * Laravel'in Ingilizce validation mesajlari (Laravel projesinde {@code lang}
 * klasoru yok, varsayilan {@code en} mesajlari kullaniliyor).
 * <p>
 * Bean Validation anotasyonlarinda {@code message} olarak kullanilir;
 * {@code :attribute} hata yonetiminde alanin JSON adiyla doldurulur:
 * <pre>@NotBlank(message = LaravelMessages.REQUIRED) String email;</pre>
 * Yeni bir kural gerektiginde metin Laravel'in
 * {@code vendor/laravel/framework/src/Illuminate/Translation/lang/en/validation.php}
 * dosyasindan birebir alinir.
 */
public final class LaravelMessages {

	public static final String REQUIRED = "The :attribute field is required.";
	public static final String STRING = "The :attribute field must be a string.";
	public static final String INTEGER = "The :attribute field must be an integer.";
	public static final String NUMERIC = "The :attribute field must be a number.";
	public static final String BOOLEAN = "The :attribute field must be true or false.";
	public static final String ARRAY = "The :attribute field must be an array.";
	public static final String EMAIL = "The :attribute field must be a valid email address.";
	public static final String DATE = "The :attribute field must be a valid date.";
	public static final String IN = "The selected :attribute is invalid.";
	public static final String EXISTS = "The selected :attribute is invalid.";
	public static final String UNIQUE = "The :attribute has already been taken.";
	public static final String MAX_255 = "The :attribute field must not be greater than 255 characters.";

	private LaravelMessages() {
	}

	/** {@code min:N} (sayisal alan). */
	public static String minNumeric(Object min) {
		return "The :attribute field must be at least " + min + ".";
	}

	/** {@code max:N} (sayisal alan). */
	public static String maxNumeric(Object max) {
		return "The :attribute field must not be greater than " + max + ".";
	}

	/** {@code min:N} (metin alan). */
	public static String minString(int min) {
		return "The :attribute field must be at least " + min + " characters.";
	}

	/** {@code max:N} (metin alan). */
	public static String maxString(int max) {
		return "The :attribute field must not be greater than " + max + " characters.";
	}

	/** {@code date_format:F}. */
	public static String dateFormat(String format) {
		return "The :attribute field must match the format " + format + ".";
	}

	/**
	 * Laravel'in alan adini mesaja yazma bicimi: {@code per_page -> "per page"}.
	 * Ic ice alanlarda nokta korunur: {@code items.0.quantity -> "items.0.quantity"}.
	 */
	public static String attribute(String field) {
		return field.replace('_', ' ');
	}

	/** Mesaj sablonundaki {@code :attribute}'u doldurur. */
	public static String format(String template, String field) {
		return template.replace(":attribute", attribute(field));
	}

	/**
	 * 422 cevabindaki ust {@code message}: ilk hata, digerleri sayi olarak.
	 * {@code "The email field is required. (and 1 more error)"}
	 */
	public static String summary(Map<String, List<String>> errors) {
		List<String> all = errors.values().stream().flatMap(List::stream).toList();
		if (all.isEmpty()) {
			return "The given data was invalid.";
		}
		int more = all.size() - 1;
		if (more == 0) {
			return all.getFirst();
		}
		return all.getFirst() + " (and " + more + " more " + (more == 1 ? "error" : "errors") + ")";
	}

}
