package com.olivamore.api.sales.order;

import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.List;

/** {@code null} gelen alan degismez; verilen kalemler eskilerin yerine gecer. */
record UpdateOrderRequest(
	Long customerId,

	Long deliveryAddressId,

	@JsonDeserialize(using = IsoDateTimeDeserializer.class)
	LocalDateTime orderDate,

	Patch<String> note,

	@Size(min = 1, message = MessageKeys.ITEMS_MIN)
	List<@Valid OrderItemRequest> items) {

	UpdateOrderRequest {
		note = Texts.trim(note);
	}

}
