package com.olivamore.api.product.productionproduct;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Yogunluk degerleri kolondaki gibi 4 basamakla tutulur. */
public final class Density {

	private static final int SCALE = 4;

	private Density() {
	}

	public static BigDecimal normalize(BigDecimal value) {
		return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
	}

}
