package com.olivamore.api.sales.order;

import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.List;

record CreateOrderRequest(
	@NotNull(message = MessageKeys.REQUIRED)
	Long customerId,

	@NotNull(message = MessageKeys.REQUIRED)
	Long deliveryAddressId,

	@NotNull(message = MessageKeys.REQUIRED)
	@JsonDeserialize(using = IsoDateTimeDeserializer.class)
	LocalDateTime orderDate,

	String note,

	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid OrderItemRequest> items) {

	CreateOrderRequest {
		note = Texts.trim(note);
	}

}
