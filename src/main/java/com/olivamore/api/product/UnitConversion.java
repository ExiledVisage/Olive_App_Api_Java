package com.olivamore.api.product;

import com.olivamore.api.shared.error.BusinessRuleException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/** KG ile LITRE arasinda cevrim: kg = litre x yogunluk (kg/L). Birimler veritabanindaki metinler. */
public final class UnitConversion {

	public static final String KG = "KG";
	public static final String LITRE = "LITRE";

	private static final MathContext PRECISION = MathContext.DECIMAL128;

	private UnitConversion() {
	}

	/** Tipin kendi yogunlugu pozitifse o, yoksa kategorinin varsayilani pozitifse o; ikisi de yoksa null. */
	public static BigDecimal effectiveDensity(BigDecimal own, BigDecimal fallback) {
		if (own != null && own.signum() > 0) {
			return own;
		}
		return fallback != null && fallback.signum() > 0 ? fallback : null;
	}

	/**
	 * Ayni birimse oldugu gibi, degilse yogunlukla.
	 * @param typeName hata mesajinda; null ise "Bu urun tipi"
	 * @throws BusinessRuleException cevrim gerekip yogunluk yoksa ya da birim desteklenmiyorsa
	 */
	public static BigDecimal convert(BigDecimal quantity, String from, String to, BigDecimal density,
		String typeName) {
		BigDecimal converted = convertOrNull(quantity, from, to, density);
		if (converted != null) {
			return converted;
		}
		if (density == null || density.signum() <= 0) {
			String name = typeName != null ? typeName : "Bu ürün tipi";
			throw new BusinessRuleException(name + " için yoğunluk tanımlı değil; " + from + " ile " + to
				+ " arasında çevrim yapılamaz. Ürün tipine yoğunluk (kg/L) girilmeli.");
		}
		throw new BusinessRuleException("Desteklenmeyen birim çevrimi: " + from + " -> " + to + ".");
	}

	/** {@link #convert} gibi; cevrilemiyorsa (yogunluk yok, birim desteklenmiyor) null. */
	public static BigDecimal convertOrNull(BigDecimal quantity, String from, String to, BigDecimal density) {
		if (Objects.equals(from, to)) {
			return quantity;
		}
		if (density == null || density.signum() <= 0) {
			return null;
		}
		if (LITRE.equals(from) && KG.equals(to)) {
			return quantity.multiply(density);
		}
		if (KG.equals(from) && LITRE.equals(to)) {
			return quantity.divide(density, PRECISION);
		}
		return null;
	}

}
