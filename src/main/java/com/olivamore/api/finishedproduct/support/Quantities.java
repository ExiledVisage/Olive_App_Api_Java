package com.olivamore.api.finishedproduct.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Hacim, oran ve miktar degerleri: kolondaki gibi 4 basamak, karsilastirmada kucuk tolerans. */
public final class Quantities {

	public static final int SCALE = 4;

	private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

	private Quantities() {
	}

	public static BigDecimal normalize(BigDecimal value) {
		return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
	}

	/** Mesajlar icin: gereksiz sifirlar olmadan. */
	public static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	/** Iki deger toleransi asacak kadar farkli mi. */
	public static boolean differ(BigDecimal a, BigDecimal b) {
		return a.subtract(b).abs().compareTo(TOLERANCE) > 0;
	}

}
