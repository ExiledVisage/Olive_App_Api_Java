package com.olivamore.api.sales.order;

import com.olivamore.api.shared.json.FlexibleDateTimeDeserializer;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.List;

/** {@code null} gelen alan degismez; verilen kalemler eskilerin yerine gecer. */
record UpdateOrderRequest(
	Long customerId,

	Long deliveryAddressId,

	@JsonDeserialize(using = FlexibleDateTimeDeserializer.class)
	LocalDateTime orderDate,

	Patch<String> note,

	@Size(min = 1, message = LaravelMessages.MIN_ITEMS_1)
	List<@Valid OrderItemRequest> items) {
}
