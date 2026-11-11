package com.olivamore.api.inventory;

import com.olivamore.api.inventory.material.Quantities;
import com.olivamore.api.product.UnitConversion;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Tank karisimindan oransal pay: tank sayfasindaki cikis, transfer ve sayim azalisi ile dolum ayni kurali kullanir.
 * Her materyal kendi tipinin yogunluguyla ortak birime cevrilir, oran bu birimde bulunur; pay materyalin kendi
 * biriminde (4 basamak) yazilir ve yuvarlama farki son materyale kalir.
 */
public final class MixShares {

	private static final BigDecimal EPSILON = new BigDecimal("0.00005");

	/** Karisimdaki bir materyal: miktari, birimi ve tipinin etkin yogunlugu (yoksa null). */
	public record Part(BigDecimal quantity, String unit, BigDecimal density, String typeName) {
	}

	private MixShares() {
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
	 * Istenen miktarin (ortak birimde) parcalara dagilimi, her parcanin kendi biriminde. Istenen toplam kadarsa her
	 * parcanin tamami. Istenenin toplami asmamasi cagiranin isidir.
	 */
	public static List<BigDecimal> split(List<Part> parts, BigDecimal requested, String unit) {
		List<BigDecimal> common = inUnit(parts, unit);
		BigDecimal total = common.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		List<BigDecimal> shares = new ArrayList<>();
		if (total.subtract(requested).abs().compareTo(EPSILON) <= 0) {
			parts.forEach(p -> shares.add(p.quantity()));
			return shares;
		}
		BigDecimal ratio = total.signum() > 0 ? requested.divide(total, MathContext.DECIMAL128) : BigDecimal.ZERO;
		BigDecimal taken = BigDecimal.ZERO;
		for (int i = 0; i < parts.size(); i++) {
			Part part = parts.get(i);
			if (i < parts.size() - 1) {
				BigDecimal share = Quantities.material(part.quantity().multiply(ratio));
				taken = taken.add(UnitConversion.convert(share, part.unit(), unit, part.density(), part.typeName()));
				shares.add(share);
			}
			else {
				BigDecimal rest = UnitConversion.convert(requested.subtract(taken), unit, part.unit(), part.density(),
					part.typeName());
				shares.add(Quantities.material(rest).max(BigDecimal.ZERO).min(part.quantity()));
			}
		}
		return shares;
	}

}
