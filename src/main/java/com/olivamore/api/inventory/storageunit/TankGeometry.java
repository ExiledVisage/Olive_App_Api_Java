package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.error.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dik silindir tank: alt ve ust koni (aci yataydan derece; 0 = duz). Cetvel dipten dolu yuksekligi olcer;
 * sivi yuksekligi alt koninin tepesinden = olculen + ofset. Hacim yukseklige kadar: alt koni (tepeden dolan koni),
 * silindir, ust koni (kesik koni). Sonuctan olu hacim (posa borusu vb.) duser.
 *
 * @param innerDiameterMm ic cap
 * @param cylinderHeightMm silindir (duz govde) yuksekligi
 * @param bottomConeDeg alt koni acisi
 * @param topConeDeg ust koni acisi
 * @param rulerOffsetMm cetvelin sifiri ile alt koni tepesi arasindaki fark (cetvel sifiri yukaridaysa pozitif)
 * @param deadVolumeLitre hesaptan dusen hacim
 */
public record TankGeometry(BigDecimal innerDiameterMm, BigDecimal cylinderHeightMm, BigDecimal bottomConeDeg,
	BigDecimal topConeDeg, BigDecimal rulerOffsetMm, BigDecimal deadVolumeLitre) {

	private static final double MM3_PER_LITRE = 1_000_000d;

	public TankGeometry {
		if (innerDiameterMm == null || innerDiameterMm.signum() <= 0) {
			throw new IllegalArgumentException("inner diameter");
		}
		if (cylinderHeightMm == null || cylinderHeightMm.signum() < 0) {
			throw new IllegalArgumentException("cylinder height");
		}
		bottomConeDeg = zeroIfNull(bottomConeDeg);
		topConeDeg = zeroIfNull(topConeDeg);
		rulerOffsetMm = zeroIfNull(rulerOffsetMm);
		deadVolumeLitre = zeroIfNull(deadVolumeLitre);
	}

	private double radius() {
		return innerDiameterMm.doubleValue() / 2;
	}

	private double bottomConeHeight() {
		return radius() * Math.tan(Math.toRadians(bottomConeDeg.doubleValue()));
	}

	private double topConeHeight() {
		return radius() * Math.tan(Math.toRadians(topConeDeg.doubleValue()));
	}

	/** Tankin toplam ic yuksekligi (alt koni tepesinden ust koni tepesine). */
	public double totalHeightMm() {
		return bottomConeHeight() + cylinderHeightMm.doubleValue() + topConeHeight();
	}

	/** Cetvelde okunabilecek en buyuk deger. */
	public BigDecimal maxReadingMm() {
		return BigDecimal.valueOf(totalHeightMm() - rulerOffsetMm.doubleValue()).setScale(0, RoundingMode.DOWN);
	}

	/** Tamamen dolu tankin hacmi (olu hacim dusulmus), litre. */
	public BigDecimal fullLitres() {
		return litres(totalHeightMm());
	}

	/** Cetvel okumasindaki hacim, litre (2 basamak); tankin disina dusen okuma reddedilir. */
	public BigDecimal litresAt(BigDecimal readingMm) {
		double height = readingMm.doubleValue() + rulerOffsetMm.doubleValue();
		if (height < 0) {
			throw new BusinessRuleException("Okuma cetvel ofsetinin altında; sıvı yüksekliği negatif olamaz.");
		}
		if (height > totalHeightMm() + 0.5) {
			throw new BusinessRuleException("Okuma tank yüksekliğini aşıyor; en fazla " + maxReadingMm() + " mm okunabilir.");
		}
		return litres(Math.min(height, totalHeightMm()));
	}

	private BigDecimal litres(double height) {
		double r = radius();
		double hb = bottomConeHeight();
		double hc = cylinderHeightMm.doubleValue();
		double ht = topConeHeight();
		double volume;
		if (height <= hb) {
			// tepeden dolan koni: yaricap yukseklikle dogrusal
			volume = hb == 0 ? 0 : Math.PI * r * r * Math.pow(height, 3) / (3 * hb * hb);
		}
		else if (height <= hb + hc) {
			volume = Math.PI * r * r * hb / 3 + Math.PI * r * r * (height - hb);
		}
		else {
			double x = Math.min(height - hb - hc, ht);
			double top = ht == 0 ? r : r * (1 - x / ht);
			volume = Math.PI * r * r * hb / 3 + Math.PI * r * r * hc + Math.PI * x / 3 * (r * r + r * top + top * top);
		}
		double litres = volume / MM3_PER_LITRE - deadVolumeLitre.doubleValue();
		return BigDecimal.valueOf(Math.max(0, litres)).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal zeroIfNull(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

}
