package com.olivamore.api.inventory.material;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Miktarlar: materyal ve attribute 4, tank bakiyesi ve hareket 2 basamak; yarim deger sifirdan uzaga. */
public final class Quantities {

	public static final int MATERIAL_SCALE = 4;
	public static final int BALANCE_SCALE = 2;

	private Quantities() {
	}

	/** materials.quantity, attribute degerleri. */
	public static BigDecimal material(BigDecimal value) {
		return value == null ? null : value.setScale(MATERIAL_SCALE, RoundingMode.HALF_UP);
	}

	/** Bakiye, hareket miktari, kapasite. */
	public static BigDecimal balance(BigDecimal value) {
		return value == null ? null : value.setScale(BALANCE_SCALE, RoundingMode.HALF_UP);
	}

	/** JSON sayisi: tam ise {@code long}, degilse {@code double}. */
	public static Number number(BigDecimal value) {
		if (value == null) {
			return null;
		}
		return value.stripTrailingZeros().scale() <= 0 ? (Number) value.longValueExact() : (Number) value.doubleValue();
	}

}
