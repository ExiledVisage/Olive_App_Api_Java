package com.olivamore.api.sales.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * KDV dahil tutardan net ve KDV (docs/INVOICES.md): kalem tutari birim fiyat x adet, net tutar kalem tutari
 * (1 + oran/100)'e bolunup kurusa yuvarlanir, KDV aradaki fark. Boylece toplam musterinin odedigine esit kalir.
 */
final class InvoiceMath {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/** Bir kalemin tutarlari. */
	record Amounts(BigDecimal gross, BigDecimal net, BigDecimal vat) {
	}

	/** Oran basina toplam (faturadaki KDV dokumu). */
	record VatGroup(int rate, BigDecimal net, BigDecimal vat) {
	}

	private InvoiceMath() {
	}

	static Amounts line(BigDecimal unitPrice, int quantity, int vatRate) {
		BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
		BigDecimal divisor = BigDecimal.ONE.add(BigDecimal.valueOf(vatRate).divide(HUNDRED));
		BigDecimal net = gross.divide(divisor, 2, RoundingMode.HALF_UP);
		return new Amounts(gross, net, gross.subtract(net));
	}

	static Amounts total(List<Amounts> lines) {
		BigDecimal gross = BigDecimal.ZERO.setScale(2);
		BigDecimal net = BigDecimal.ZERO.setScale(2);
		BigDecimal vat = BigDecimal.ZERO.setScale(2);
		for (Amounts line : lines) {
			gross = gross.add(line.gross());
			net = net.add(line.net());
			vat = vat.add(line.vat());
		}
		return new Amounts(gross, net, vat);
	}

	/** Oranlar kucukten buyuge. */
	static List<VatGroup> byRate(Map<Integer, List<Amounts>> lines) {
		Map<Integer, Amounts> sums = new TreeMap<>();
		lines.forEach((rate, amounts) -> sums.put(rate, total(amounts)));
		return sums.entrySet().stream().map(e -> new VatGroup(e.getKey(), e.getValue().net(), e.getValue().vat()))
			.toList();
	}

}
