package com.olivamore.api.finishedproduct.run;


import java.math.BigDecimal;

record AttributeValueResponse(
	Long id,
	BigDecimal estimatedValue,
	BigDecimal actualValue) {
}
