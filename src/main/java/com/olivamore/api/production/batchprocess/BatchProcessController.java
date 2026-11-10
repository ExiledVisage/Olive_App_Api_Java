package com.olivamore.api.production.batchprocess;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Proses detayi, alan degerleri, durum gecisleri ve onay/red; cevaplar detay iliskileriyle, 200. */
@RestController
class BatchProcessController {

	private final BatchProcessResponses responses;
	private final BatchProcessService processes;

	BatchProcessController(BatchProcessResponses responses, BatchProcessService processes) {
		this.responses = responses;
		this.processes = processes;
	}

	@GetMapping("/api/batch-processes/{id}")
	@PreAuthorize("hasAuthority('batch_process.view')")
	DataResponse<BatchProcessResponse> show(@PathVariable long id) {
		return detail(id);
	}

	/** Degerler alanin veri tipine gore dogrulanir ve ilgili value_* kolonuna yazilir. */
	@PostMapping("/api/batch-processes/{id}/sync-field-values")
	@PreAuthorize("hasAuthority('batch_process.fill')")
	DataResponse<BatchProcessResponse> syncFieldValues(@PathVariable long id,
		@Valid @RequestBody SyncFieldValuesRequest request) {
		processes.syncFieldValues(id, request);
		return detail(id);
	}

	/** Yetki {@code operation_data} kurallarindan once (403). */
	@PatchMapping("/api/batch-processes/{id}")
	@PreAuthorize("hasAuthority('batch_process.view')")
	DataResponse<BatchProcessResponse> update(@PathVariable long id, @RequestBody UpdateBatchProcessRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		processes.update(id, request, user);
		return detail(id);
	}

	/** Etki (materyal, tank hareketi) simdi islenir; gonderimden beri kural bozulduysa 422, asama bekler. */
	@PostMapping("/api/batch-processes/{id}/approve")
	@PreAuthorize("hasAuthority('batch_process.approve')")
	DataResponse<BatchProcessResponse> approve(@PathVariable long id,
		@Valid @RequestBody(required = false) ApproveBatchProcessRequest request, @AuthenticationPrincipal CurrentUser user) {
		processes.approve(id, user, request == null ? null : request.note());
		return detail(id);
	}

	/** Gerekce zorunlu; asama gonderilen veriyle isciye (in_progress) doner. */
	@PostMapping("/api/batch-processes/{id}/reject")
	@PreAuthorize("hasAuthority('batch_process.approve')")
	DataResponse<BatchProcessResponse> reject(@PathVariable long id, @Valid @RequestBody RejectBatchProcessRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		processes.reject(id, user, request.note());
		return detail(id);
	}

	private DataResponse<BatchProcessResponse> detail(long id) {
		return DataResponse.of(responses.detail(id));
	}

}
