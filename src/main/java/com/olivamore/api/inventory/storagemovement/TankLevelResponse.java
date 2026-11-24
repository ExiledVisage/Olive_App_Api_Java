package com.olivamore.api.inventory.storagemovement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Tank seviyesi cevaplari. */
final class TankLevelResponse {

	private TankLevelResponse() {
	}

	/** @param maxReadingMm cetvelde okunabilecek en buyuk deger; {@code fullLitres} tamamen dolu hacim */
	record Geometry(BigDecimal innerDiameterMm, BigDecimal cylinderHeightMm, BigDecimal bottomConeDeg,
		BigDecimal topConeDeg, BigDecimal rulerOffsetMm, BigDecimal deadVolumeLitre, BigDecimal maxReadingMm,
		BigDecimal fullLitres) {
	}

	/**
	 * @param bookQuantity okuma anindaki defter miktari, {@code unit} (tankin birimi)
	 * @param difference hesaplanan - defter; kg birimli tankta yogunluk bilinmiyorsa null
	 */
	record Reading(Long id, Long storageUnitId, BigDecimal measuredMm, BigDecimal calculatedLitre,
		BigDecimal calculatedKg, BigDecimal densityKgPerLitre, BigDecimal bookQuantity, String unit,
		BigDecimal difference, BigDecimal differencePercent, MeasuredBy measuredBy, String note,
		LocalDateTime measuredAt) {
	}

	record MeasuredBy(Long id, String name) {
	}

}
