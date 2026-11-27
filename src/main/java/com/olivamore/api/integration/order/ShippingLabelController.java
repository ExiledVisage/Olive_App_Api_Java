package com.olivamore.api.integration.order;

import com.olivamore.api.shared.web.DataResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Pazar yeri siparisinin kargo etiketi; siparisi hazirlayan depo da alir (docs/INTEGRATIONS.md). */
@RestController
class ShippingLabelController {

	private final ShippingLabels labels;

	ShippingLabelController(ShippingLabels labels) {
		this.labels = labels;
	}

	/** @param orderId bizdeki siparis */
	@GetMapping("/api/integrations/sales-orders/{orderId}/label")
	@PreAuthorize("hasAnyAuthority('integration.view', 'order.update_status')")
	DataResponse<ShippingLabels.Label> label(@PathVariable long orderId) {
		return DataResponse.of(labels.label(orderId));
	}

}
