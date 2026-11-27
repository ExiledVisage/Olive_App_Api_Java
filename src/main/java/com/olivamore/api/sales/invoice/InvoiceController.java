package com.olivamore.api.sales.invoice;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.Pages;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Satis faturalari: siparisten kesme, liste, detay, iptal (docs/INVOICES.md). */
@RestController
class InvoiceController {

	private final InvoiceService service;
	private final InvoiceQueries queries;

	InvoiceController(InvoiceService service, InvoiceQueries queries) {
		this.service = service;
		this.queries = queries;
	}

	@GetMapping("/api/invoices")
	@PreAuthorize("hasAuthority('invoice.view')")
	PagedModel<InvoiceResponse> index(@RequestParam(required = false) String status,
		@RequestParam(name = "customer_id", required = false) Long customerId,
		@RequestParam(name = "order_id", required = false) Long orderId, Pageable pageable) {
		InvoiceQueries.Filter filter = new InvoiceQueries.Filter(status, customerId, orderId);
		return Pages.of(pageable, requested -> queries.page(filter, requested), () -> queries.list(filter));
	}

	@GetMapping("/api/invoices/{id}")
	@PreAuthorize("hasAuthority('invoice.view')")
	DataResponse<InvoiceResponse> show(@PathVariable long id) {
		return DataResponse.of(queries.detail(id));
	}

	@PostMapping("/api/orders/{orderId}/invoice")
	@PreAuthorize("hasAuthority('invoice.manage')")
	ResponseEntity<DataResponse<InvoiceResponse>> issue(@PathVariable long orderId,
		@Valid @RequestBody(required = false) IssueInvoiceRequest request, @AuthenticationPrincipal CurrentUser user) {
		long id = service.issue(orderId, request == null ? IssueInvoiceRequest.EMPTY : request, user.id());
		return DataResponse.created(queries.detail(id));
	}

	@PostMapping("/api/invoices/{id}/cancel")
	@PreAuthorize("hasAuthority('invoice.manage')")
	DataResponse<InvoiceResponse> cancel(@PathVariable long id, @Valid @RequestBody CancelInvoiceRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		service.cancel(id, request, user.id());
		return DataResponse.of(queries.detail(id));
	}

}
