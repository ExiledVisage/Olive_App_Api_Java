package com.olivamore.api.production.batchprocess;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Laravel {@code BatchProcessController}: proses detayi, alan degerleri, durum gecisleri (baslatma, tamamlama, atlama,
 * geri acma, basarisizlik) ve onay/red. Cevaplar BatchProcessResource (detay iliskileriyle), 200.
 */
@RestController
class BatchProcessController {

	private static final String TABLE = "batch_processes";
	private static final String MODEL = "BatchProcess";

	private final Db db;
	private final LaravelValidator validator;
	private final BatchProcessResource resource;
	private final BatchProcessService processes;
	private final BatchProcessRequests requests;

	BatchProcessController(Db db, LaravelValidator validator, BatchProcessResource resource, BatchProcessService processes,
		BatchProcessRequests requests) {
		this.db = db;
		this.validator = validator;
		this.resource = resource;
		this.processes = processes;
		this.requests = requests;
	}

	@GetMapping("/api/batch-processes/{batchProcess}")
	@RequiresPermission("batch_process.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process) {
		return detail(process.id());
	}

	/** Degerler alanin veri tipine gore dogrulanir ve ilgili value_* kolonuna yazilir. */
	@PostMapping("/api/batch-processes/{batchProcess}/sync-field-values")
	@RequiresPermission("batch_process.fill")
	@Transactional
	DataResponse<Map<String, Object>> syncFieldValues(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, BatchProcessRequests.syncFieldValues(), requests.fieldValueTypes());
		processes.syncFieldValues(process, v.get("fields"));
		return detail(process.id());
	}

	/** Yetki dogrulamadan once (403); tamamlanirken {@code operation_data} asamanin uzantilarina gore. */
	@PatchMapping("/api/batch-processes/{batchProcess}")
	@RequiresPermission("batch_process.view")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input, CurrentUser user) {
		requests.authorizeUpdate(process, input, user);
		processes.update(process, validator.validate(input, requests.update(process, input)), user);
		return detail(process.id());
	}

	/** Etki (materyal, tank hareketi) simdi islenir; gonderimden beri kural bozulduysa 422, asama bekler. */
	@PostMapping("/api/batch-processes/{batchProcess}/approve")
	@RequiresPermission("batch_process.approve")
	@Transactional
	DataResponse<Map<String, Object>> approve(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, BatchProcessRequests.approve());
		processes.approve(process, user, v.get("note"));
		return detail(process.id());
	}

	/** Gerekce zorunlu; asama gonderilen veriyle isciye (in_progress) doner. */
	@PostMapping("/api/batch-processes/{batchProcess}/reject")
	@RequiresPermission("batch_process.approve")
	@Transactional
	DataResponse<Map<String, Object>> reject(@Bound(value = "batchProcess", table = TABLE, model = MODEL) Row process,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, BatchProcessRequests.reject());
		processes.reject(process, user, v.get("note"));
		return detail(process.id());
	}

	private DataResponse<Map<String, Object>> detail(long id) {
		Row process = db.find(TABLE, id).orElseThrow();
		return DataResponse.of(resource.process(process, processes.requiresApproval(process)));
	}

}
