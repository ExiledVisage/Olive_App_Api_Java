package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Teslimatin alim bilgisi; istek bilginin tamamidir (verilmeyen alan temizlenir). Fiyat KDV haric TL/kg, KDV
 * orani yuzde; fiyat verildiyse oran zorunlu. Nakliye toplam tutar (KDV'den bagimsiz, oldugu gibi eklenir).
 */
record UpdatePurchaseRequest(
	SupplyChannel supplyChannel,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal pricePerKg,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@DecimalMax(value = "100", message = MessageKeys.MAX)
	BigDecimal vatRate,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal transportCost,

	String priceNote) {

	UpdatePurchaseRequest {
		priceNote = Texts.trim(priceNote);
	}

}
