package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Son tank analizi: tank materyali ve olculen sayisal degerler. */
record TankMeasurementRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long materialId,

	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid Attribute> attributes,

	String note) {

	TankMeasurementRequest {
		note = Texts.trim(note);
	}

	record Attribute(
		@NotNull(message = MessageKeys.REQUIRED)
		Long productionProductAttributeDefinitionId,

		@NotNull(message = MessageKeys.REQUIRED)
		BigDecimal value) {
	}

}
