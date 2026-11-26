package com.olivamore.api.integration.order;

import com.olivamore.api.integration.sync.SyncRuns;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Site ve pazar yeri siparisleri (docs/INTEGRATIONS.md 0). */
@RestController
class OrderPullController {

	private final OrderPull pull;
	private final PreparingReport preparing;

	OrderPullController(OrderPull pull, PreparingReport preparing) {
		this.pull = pull;
		this.preparing = preparing;
	}

	/** @param reported platforma bildirilen siparis sayisi */
	record Reported(int reported) {
	}

	/** Simdi cek: bekleyenleri yeniden dener, yeni siparisleri alir. */
	@PostMapping("/api/integrations/connections/{id}/orders/pull")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<OrderPull.Pull> pull(@PathVariable long id) {
		return DataResponse.of(pull.pull(id, SyncRuns.Trigger.MANUAL));
	}

	/** Inceleme bekleyenleri yeniden dene (urun eslestirildikten sonra). */
	@PostMapping("/api/integrations/connections/{id}/orders/retry")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<OrderPull.Pull> retry(@PathVariable long id) {
		return DataResponse.of(pull.retry(id));
	}

	/** Hazirlaniyor bildirimi reddedilen ya da bildirilmemis siparisleri simdi yeniden bildir. */
	@PostMapping("/api/integrations/connections/{id}/orders/report")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<Reported> report(@PathVariable long id) {
		return DataResponse.of(new Reported(preparing.retry(id, true)));
	}

	/** {@code status}: imported ya da pending; en yeni 200. */
	@GetMapping("/api/integrations/connections/{id}/orders")
	@PreAuthorize("hasAuthority('integration.view')")
	ListResponse<OrderPull.IntegrationOrder> orders(@PathVariable long id,
		@RequestParam(name = "status", required = false) String status) {
		return new ListResponse<>(pull.list(id, status));
	}

}
