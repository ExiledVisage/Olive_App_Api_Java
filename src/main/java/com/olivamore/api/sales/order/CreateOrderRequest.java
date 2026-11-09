package com.olivamore.api.sales.order;

import com.olivamore.api.shared.json.FlexibleDateTimeDeserializer;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.List;

record CreateOrderRequest(
	@NotNull(message = LaravelMessages.REQUIRED)
	Long customerId,

	@NotNull(message = LaravelMessages.REQUIRED)
	Long deliveryAddressId,

	@NotNull(message = LaravelMessages.REQUIRED)
	@JsonDeserialize(using = FlexibleDateTimeDeserializer.class)
	LocalDateTime orderDate,

	String note,

	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<@Valid OrderItemRequest> items) {
}
