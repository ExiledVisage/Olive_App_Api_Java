package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Tank geometrisi (mm, derece, litre); istek bilginin tamami. Koni acilari yataydan, 0 = duz. Cetvel ofseti
 * cetvelin sifiri ile alt koni tepesi arasindaki fark; olu hacim (posa borusu vb.) hesaptan duser.
 */
record TankGeometryRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
	BigDecimal innerDiameterMm,

	@NotNull(message = MessageKeys.REQUIRED)
	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal cylinderHeightMm,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@DecimalMax(value = "80", message = MessageKeys.MAX)
	BigDecimal bottomConeDeg,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	@DecimalMax(value = "80", message = MessageKeys.MAX)
	BigDecimal topConeDeg,

	BigDecimal rulerOffsetMm,

	@DecimalMin(value = "0", message = MessageKeys.MIN)
	BigDecimal deadVolumeLitre) {
}
