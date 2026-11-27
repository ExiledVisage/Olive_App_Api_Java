package com.olivamore.api.integration.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Platform cevabindaki fiyatlar: sayi ya da metin; birim fiyat kurusa yuvarlanir. */
public final class Prices {

	private Prices() {
	}

	/** Sayi ya da sayi metni; okunamazsa bos. */
	public static BigDecimal decimal(Object value) {
		if (value instanceof Number n) {
			return new BigDecimal(n.toString());
		}
		if (value instanceof String s && !s.isBlank()) {
			try {
				return new BigDecimal(s.trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/** Kalem tutarindan birim fiyat; tutar ya da adet yoksa bos. */
	public static BigDecimal perUnit(BigDecimal lineTotal, int quantity) {
		if (lineTotal == null || quantity <= 0) {
			return null;
		}
		return lineTotal.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
	}

	/** Kurusa yuvarlanmis; bos ya da negatifse bos. */
	public static BigDecimal money(BigDecimal value) {
		return value == null || value.signum() < 0 ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

}
