package com.olivamore.api.production.batch;

import com.olivamore.api.production.batch.ProductionBatchResponse.Purchase;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Teslimatin alim tutarlari: fiyat KDV haric TL/kg; KDV dahil fiyat = fiyat x (1 + oran / 100). Toplam = kilo x
 * fiyat + nakliye; kg maliyeti = toplam / kilo. Grupta teslimatin fiyati ve nakliyenin kilo payi. Tutarlar 2,
 * kg fiyatlari 4 basamak.
 */
final class Purchases {

	static final String PERMISSION = "production_batch.price";

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private Purchases() {
	}

	/** Istegi yapan kullanici fiyatlari gorebilir mi. */
	static boolean visible() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority).anyMatch(PERMISSION::equals);
	}

	/**
	 * @param delivery alim bilgisinin oldugu teslimat
	 * @param quantityKg tutarin hesaplandigi kilo (teslimatin ya da grubun)
	 * @param fromDelivery grup icin true
	 * @return alim bilgisi hic girilmediyse null
	 */
	static Purchase of(ProductionBatch delivery, BigDecimal quantityKg, boolean fromDelivery) {
		if (delivery.getSupplyChannel() == null && delivery.getPricePerKg() == null
			&& delivery.getTransportCost() == null && delivery.getPriceNote() == null) {
			return null;
		}
		BigDecimal price = delivery.getPricePerKg();
		BigDecimal vat = delivery.getVatRate();
		BigDecimal priceWithVat = price == null ? null : withVat(price, vat).setScale(4, RoundingMode.HALF_UP);
		BigDecimal transport = transportShare(delivery, quantityKg, fromDelivery);
		BigDecimal kg = quantityKg == null ? BigDecimal.ZERO : quantityKg;
		BigDecimal totalEx = total(price, kg, transport);
		BigDecimal totalWith = total(priceWithVat, kg, transport);
		return new Purchase(delivery.getSupplyChannel(), price, vat, priceWithVat, transport, totalEx, totalWith,
			perKg(totalEx, kg), perKg(totalWith, kg), delivery.getPriceNote(), fromDelivery);
	}

	private static BigDecimal withVat(BigDecimal price, BigDecimal vat) {
		return vat == null ? price : price.multiply(BigDecimal.ONE.add(vat.divide(HUNDRED)));
	}

	/** Grupta nakliye teslimat kilosuna gore paylastirilir. */
	private static BigDecimal transportShare(ProductionBatch delivery, BigDecimal quantityKg, boolean fromDelivery) {
		BigDecimal transport = delivery.getTransportCost();
		if (transport == null || !fromDelivery) {
			return transport;
		}
		BigDecimal deliveryKg = delivery.getQuantityKg();
		if (deliveryKg == null || deliveryKg.signum() <= 0 || quantityKg == null) {
			return null;
		}
		return transport.multiply(quantityKg).divide(deliveryKg, 2, RoundingMode.HALF_UP);
	}

	private static BigDecimal total(BigDecimal price, BigDecimal kg, BigDecimal transport) {
		if (price == null && transport == null) {
			return null;
		}
		BigDecimal goods = price == null ? BigDecimal.ZERO : price.multiply(kg);
		return goods.add(transport == null ? BigDecimal.ZERO : transport).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal perKg(BigDecimal total, BigDecimal kg) {
		return total == null || kg.signum() <= 0 ? null : total.divide(kg, 4, RoundingMode.HALF_UP);
	}

}
