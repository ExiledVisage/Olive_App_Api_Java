package com.olivamore.api.shared.validation;

/**
 * {@code messages.properties} anahtarlari. Anotasyonlarda Bean Validation bicimiyle kullanilir:
 * <pre>@NotBlank(message = MessageKeys.REQUIRED) String email;</pre>
 * {@code {attribute}} hata cevabinda alanin okunur adiyla, {@code {max}}, {@code {value}} gibi degiskenler
 * kuralin degerleriyle doldurulur.
 */
public final class MessageKeys {

	public static final String REQUIRED = "{validation.required}";
	public static final String STRING = "{validation.string}";
	public static final String INTEGER = "{validation.integer}";
	public static final String NUMERIC = "{validation.numeric}";
	public static final String BOOLEAN = "{validation.boolean}";
	public static final String ARRAY = "{validation.array}";
	public static final String EMAIL = "{validation.email}";
	public static final String DATE = "{validation.date}";
	/** Degisken: {@code format}. */
	public static final String DATE_FORMAT = "{validation.date-format}";
	public static final String IN = "{validation.in}";
	public static final String EXISTS = "{validation.exists}";
	public static final String UNIQUE = "{validation.unique}";
	public static final String CONFIRMED = "{validation.confirmed}";
	public static final String DISTINCT = "{validation.distinct}";
	/** {@code @Size(min = N)} metin. */
	public static final String SIZE_MIN = "{validation.size.min}";
	/** {@code @Size(max = N)} metin; elle kullaniminda degisken: {@code max}. */
	public static final String SIZE_MAX = "{validation.size.max}";
	/** {@code @Size(min = N)} liste. */
	public static final String ITEMS_MIN = "{validation.items.min}";
	/** {@code @Min}, {@code @DecimalMin}. */
	public static final String MIN = "{validation.min}";
	/** {@code @Max}, {@code @DecimalMax}. */
	public static final String MAX = "{validation.max}";
	/** {@code @DecimalMin(inclusive = false)}; elle kullaniminda degisken: {@code value}. */
	public static final String GT = "{validation.gt}";
	public static final String BETWEEN_90 = "{validation.between.90}";
	public static final String BETWEEN_180 = "{validation.between.180}";
	public static final String REQUIRED_WITH_CONTENTS = "{validation.required-with.contents}";
	public static final String REQUIRED_WITH_COMPONENTS = "{validation.required-with.components}";
	public static final String REQUIRED_WITH_PLACEMENTS = "{validation.required-with.placements}";
	/** Degisken: {@code other}. */
	public static final String REQUIRED_WITH = "{validation.required-with}";
	/** Degisken: {@code other}. */
	public static final String REQUIRED_WITHOUT = "{validation.required-without}";
	/** Degisken: {@code others}. */
	public static final String REQUIRED_WITHOUT_ALL = "{validation.required-without-all}";
	/** Degisken: {@code other}. */
	public static final String PROHIBITS = "{validation.prohibits}";
	/** Degisken: {@code other}. */
	public static final String DIFFERENT = "{validation.different}";
	/** Degisken: {@code other}. */
	public static final String AFTER_OR_EQUAL = "{validation.after-or-equal}";

	private MessageKeys() {
	}

}
