package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/** Dolum kosulari. */
@RestController
class FinishedProductRunController {

	private final FinishedProductRunService runs;
	private final FinishedProductRunResponses responses;

	FinishedProductRunController(FinishedProductRunService runs, FinishedProductRunResponses responses) {
		this.runs = runs;
		this.responses = responses;
	}

	/** En yeni once; {@code per_page} ile sayfali. */
	@GetMapping("/api/finished-product-runs")
	@RequiresPermission("finished_product_run.view")
	ListResponse<FinishedProductRunResponse> index(HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(responses.list());
		}
		int page = Listing.currentPage(request);
		Page<FinishedProductRunResponse> rows = responses.page(page, perPage.get());
		return LaravelPaginator.paginate(rows.getContent(), rows.getTotalElements(), page, perPage.get(), request);
	}

	@GetMapping("/api/finished-product-runs/{id}")
	@RequiresPermission("finished_product_run.view")
	DataResponse<FinishedProductRunResponse> show(@PathVariable long id) {
		return DataResponse.of(responses.detail(id));
	}

	/** Planlama (201). */
	@PostMapping("/api/finished-product-runs")
	@RequiresPermission("finished_product_run.create")
	ResponseEntity<DataResponse<FinishedProductRunResponse>> store(@Valid @RequestBody CreateRunRequest request) {
		return DataResponse.created(responses.written(runs.create(request)));
	}

	@PatchMapping("/api/finished-product-runs/{id}/status")
	@RequiresPermission("finished_product_run.complete")
	DataResponse<FinishedProductRunResponse> updateStatus(@PathVariable long id,
		@Valid @RequestBody UpdateRunStatusRequest request, CurrentUser user) {
		runs.updateStatus(id, request, user.id());
		return DataResponse.of(responses.written(id));
	}

	/** Gercek attribute degeri; deger baska kosununsa 404. */
	@PatchMapping("/api/finished-product-runs/{id}/attribute-values/{attributeValue}")
	@RequiresPermission("finished_product_run.complete")
	AttributeValueResponse updateAttributeValue(@PathVariable long id, @PathVariable long attributeValue,
		@Valid @RequestBody UpdateAttributeValueRequest request) {
		return runs.updateAttributeValue(id, attributeValue, request);
	}

	/** Ambalaj lotu tahsisinin elle degistirilmesi. */
	@PutMapping("/api/finished-product-runs/{id}/packagings")
	@RequiresPermission("finished_product_run.create")
	DataResponse<FinishedProductRunResponse> syncPackagings(@PathVariable long id,
		@Valid @RequestBody SyncRunPackagingsRequest request) {
		runs.syncPackagings(id, request);
		return DataResponse.of(responses.detail(id));
	}

	@DeleteMapping("/api/finished-product-runs/{id}")
	@RequiresPermission("finished_product_run.create")
	Map<String, String> destroy(@PathVariable long id) {
		runs.delete(id);
		return Map.of("message", "Run başarıyla silindi.");
	}

}
