package com.olivamore.api.integration.stock;

import com.olivamore.api.integration.sync.SyncRuns;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stok gonderimi (docs/INTEGRATIONS.md 0). */
@RestController
class StockPushController {

	private final StockPush stock;

	StockPushController(StockPush stock) {
		this.stock = stock;
	}

	/** Eslesmis urunlerin satilabilir stogu ve gonderilecek mi (gonderim kapali da olsa). */
	@GetMapping("/api/integrations/connections/{id}/stock")
	@PreAuthorize("hasAuthority('integration.view')")
	ListResponse<StockPush.Line> preview(@PathVariable long id) {
		return new ListResponse<>(stock.preview(id));
	}

	/** Simdi gonder: yalniz degisenler. */
	@PostMapping("/api/integrations/connections/{id}/stock/push")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<StockPush.Push> push(@PathVariable long id) {
		return DataResponse.of(stock.push(id, SyncRuns.Trigger.MANUAL));
	}

}
