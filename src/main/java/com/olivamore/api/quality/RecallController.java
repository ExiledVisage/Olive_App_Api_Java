package com.olivamore.api.quality;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Geri cagirma ve izlenebilirlik tatbikati (docs/QUALITY.md 3). */
@RestController
class RecallController {

	private final Recalls recalls;
	private final RecallSources sources;

	RecallController(Recalls recalls, RecallSources sources) {
		this.recalls = recalls;
		this.sources = sources;
	}

	/** {@code ?kind} (recall, drill), {@code ?status} (open, completed, cancelled) suzgec. */
	@GetMapping("/api/quality/recalls")
	@PreAuthorize("hasAuthority('quality.view')")
	ListResponse<Recalls.Recall> index(@RequestParam(name = "kind", required = false) String kind,
		@RequestParam(name = "status", required = false) String status) {
		return new ListResponse<>(recalls.all(kind, status));
	}

	@GetMapping("/api/quality/recalls/{id}")
	@PreAuthorize("hasAuthority('quality.view')")
	DataResponse<Recalls.Detail> show(@PathVariable long id) {
		return DataResponse.of(recalls.get(id));
	}

	/** Kaynak secicisi: {@code type} delivery, tank_material ya da run; {@code q} arama. */
	@GetMapping("/api/quality/recall-sources")
	@PreAuthorize("hasAuthority('quality.view')")
	ListResponse<RecallSources.Option> sources(@RequestParam("type") String type,
		@RequestParam(name = "q", required = false) String query) {
		return new ListResponse<>(sources.search(type, query));
	}

	@PostMapping("/api/quality/recalls")
	@PreAuthorize("hasAuthority('quality.manage')")
	ResponseEntity<DataResponse<Recalls.Detail>> store(@RequestBody Recalls.CreateRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(recalls.create(request, user.id()));
	}

	@PostMapping("/api/quality/recalls/{id}/retrace")
	@PreAuthorize("hasAuthority('quality.manage')")
	DataResponse<Recalls.Detail> retrace(@PathVariable long id) {
		return DataResponse.of(recalls.retrace(id));
	}

	@PostMapping("/api/quality/recalls/{id}/complete")
	@PreAuthorize("hasAuthority('quality.manage')")
	DataResponse<Recalls.Detail> complete(@PathVariable long id,
		@RequestBody(required = false) Recalls.CompleteRequest request) {
		return DataResponse.of(recalls.complete(id, request));
	}

	@PostMapping("/api/quality/recalls/{id}/cancel")
	@PreAuthorize("hasAuthority('quality.manage')")
	DataResponse<Recalls.Detail> cancel(@PathVariable long id) {
		return DataResponse.of(recalls.cancel(id));
	}

	/** Siparis satiri: aranma ({@code contacted}), geri gelen adet, not. */
	@PatchMapping("/api/quality/recall-orders/{id}")
	@PreAuthorize("hasAuthority('quality.manage')")
	DataResponse<Recalls.Detail> updateOrder(@PathVariable long id, @RequestBody Recalls.OrderUpdate request) {
		return DataResponse.of(recalls.updateOrder(id, request));
	}

}
