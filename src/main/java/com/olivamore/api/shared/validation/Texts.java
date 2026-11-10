package com.olivamore.api.shared.validation;

import com.olivamore.api.shared.json.Patch;

/**
 * Metin girdisi temizligi: bastaki ve sondaki bosluk silinir, bos kalan deger verilmemis sayilir ("Ali" ile "Ali "
 * iki ayri kayit olmaz, bos not {@code null} saklanir). Istek record'larinin kurucusunda alan alan uygulanir;
 * sifre ve token alanlari oldugu gibi kalir.
 */
public final class Texts {

	private Texts() {
	}

	public static String trim(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** Alan gelmediyse {@code null} kalir; geldiyse degeri temizlenir. */
	public static Patch<String> trim(Patch<String> value) {
		return value == null ? null : new Patch<>(trim(value.value()));
	}

}
