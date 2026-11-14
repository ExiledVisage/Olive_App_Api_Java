package com.olivamore.api.inventory;

import com.olivamore.api.inventory.material.Quantities;
import com.olivamore.api.product.UnitConversion;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Tank karisimindan oransal pay: tank sayfasindaki cikis, transfer ve sayim azalisi, Bosalt ve dolum ayni kurali
 * kullanir. Oran her zaman kutle uzerinden bulunur: her materyal kendi tipinin yogunluguyla kg'ye cevrilir, baska
 * birimde istenen miktar karisimin yogunluguyla (toplam kg / toplam o birim) kg'ye cevrilir. Boylece sonuc materyal
 * sirasina ya da birimine bagli degildir. Pay materyalin kendi biriminde (4 basamak) yazilir, yuvarlama farki son
 * materyale kalir.
 */
public final class MixShares {

	private static final BigDecimal EPSILON = new BigDecimal("0.00005");

	/** Karisimdaki bir materyal: miktari, birimi ve tipinin etkin yogunlugu (yoksa null). */
	public record Part(BigDecimal quantity, String unit, BigDecimal density, String typeName) {

		BigDecimal kg() {
			return UnitConversion.convert(quantity, unit, UnitConversion.KG, density, typeName);
		}

	}

	private MixShares() {
	}

	/** Parcalarin kg karsiliklari; yogunluk yoksa islem reddedilir. */
	public static List<BigDecimal> inKg(List<Part> parts) {
		return parts.stream().map(Part::kg).toList();
	}

	/** Parcalarin verilen birimdeki miktarlari; cevrim gerekip yogunluk yoksa islem reddedilir. */
	public static List<BigDecimal> inUnit(List<Part> parts, String unit) {
		return parts.stream().map(p -> UnitConversion.convert(p.quantity(), p.unit(), unit, p.density(), p.typeName()))
			.toList();
	}

	public static BigDecimal total(List<Part> parts, String unit) {
		return inUnit(parts, unit).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/**
	 * Verilen birimdeki miktarin kg karsiligi, karisimin yogunluguyla (toplam kg / toplam o birim). Tek tipli
	 * karisimda tipin yogunlugu, cok tipli karisimda kutle agirlikli ortalama yogunluk olur.
	 */
	public static BigDecimal toKg(List<Part> parts, BigDecimal quantity, String unit) {
		if (UnitConversion.KG.equals(unit)) {
			return quantity;
		}
		BigDecimal inUnit = total(parts, unit);
		return inUnit.signum() > 0
			? quantity.multiply(total(parts, UnitConversion.KG)).divide(inUnit, MathContext.DECIMAL128)
			: BigDecimal.ZERO;
	}

	/** {@link #toKg}'nin tersi: kg miktarin verilen birimdeki karsiligi, karisimin yogunluguyla. */
	public static BigDecimal fromKg(List<Part> parts, BigDecimal kg, String unit) {
		if (UnitConversion.KG.equals(unit)) {
			return kg;
		}
		BigDecimal totalKg = total(parts, UnitConversion.KG);
		return totalKg.signum() > 0
			? kg.multiply(total(parts, unit)).divide(totalKg, MathContext.DECIMAL128)
			: BigDecimal.ZERO;
	}

	/** Miktarin bir birimden digerine karisimin yogunluguyla cevrimi; ayni birimse oldugu gibi. */
	public static BigDecimal convert(List<Part> parts, BigDecimal quantity, String from, String to) {
		return from.equals(to) ? quantity : fromKg(parts, toKg(parts, quantity, from), to);
	}

	/**
	 * Istenen miktarin ({@code unit} biriminde) parcalara dagilimi, her parcanin kendi biriminde; oran kutleyle.
	 * Istenen toplam kadarsa her parcanin tamami. Istenenin toplami asmamasi cagiranin isidir.
	 */
	public static List<BigDecimal> split(List<Part> parts, BigDecimal requested, String unit) {
		List<BigDecimal> shares = new ArrayList<>();
		if (total(parts, unit).subtract(requested).abs().compareTo(EPSILON) <= 0) {
			parts.forEach(p -> shares.add(p.quantity()));
			return shares;
		}
		BigDecimal totalKg = total(parts, UnitConversion.KG);
		BigDecimal requestedKg = toKg(parts, requested, unit);
		BigDecimal ratio = totalKg.signum() > 0 ? requestedKg.divide(totalKg, MathContext.DECIMAL128) : BigDecimal.ZERO;
		BigDecimal takenKg = BigDecimal.ZERO;
		for (int i = 0; i < parts.size(); i++) {
			Part part = parts.get(i);
			if (i < parts.size() - 1) {
				BigDecimal share = Quantities.material(part.quantity().multiply(ratio));
				takenKg = takenKg.add(UnitConversion.convert(share, part.unit(), UnitConversion.KG, part.density(),
					part.typeName()));
				shares.add(share);
			}
			else {
				BigDecimal rest = UnitConversion.convert(requestedKg.subtract(takenKg), UnitConversion.KG, part.unit(),
					part.density(), part.typeName());
				shares.add(Quantities.material(rest).max(BigDecimal.ZERO).min(part.quantity()));
			}
		}
		return shares;
	}

}
