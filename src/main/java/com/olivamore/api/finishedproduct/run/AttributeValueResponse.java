package com.olivamore.api.finishedproduct.run;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

record AttributeValueResponse(
	Long id,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	BigDecimal estimatedValue,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	BigDecimal actualValue) {
}
