package com.olivamore.api.production.batch;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Laravel {@code ProductionBatchController}: partiler, takvim. */
@RestController
class ProductionBatchController {

	private static final String TABLE = "production_batches";
	private static final String MODEL = "ProductionBatch";

	private final LaravelValidator validator;
	private final ProductionBatchService batches;
	private final ProductionBatchResource resource;

	ProductionBatchController(LaravelValidator validator, ProductionBatchService batches, ProductionBatchResource resource) {
		this.validator = validator;
		this.batches = batches;
		this.resource = resource;
	}

	// ---- okuma (production_batch.view) -------------------------------------

	/** {@code ?received_from}, {@code ?received_to} (Y-m-d, varis gunu, dahil); en yeni once; per_page ile sayfali. */
	@GetMapping("/api/production-batches")
	@RequiresPermission("production_batch.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request, RequestInput input) {
		ProductionBatchService.Filter filter = batches.filter(validator.validate(input, ProductionBatchRequests.index()));
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(resource.batches(batches.all(filter)));
		}
		int page = Listing.currentPage(request);
		long total = batches.count(filter);
		List<Row> rows = total == 0 ? List.of() : batches.page(filter, page, perPage.get());
		return LaravelPaginator.paginate(resource.batches(rows), total, page, perPage.get(), request);
	}

	/** Gun -> parti sayisi (varis gunu); bos aralikta {@code {}}. */
	@GetMapping("/api/production-batches/calendar")
	@RequiresPermission("production_batch.view")
	Map<String, Object> calendar(RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionBatchRequests.calendar());
		return Map.of("data", batches.calendar(v.get("from"), v.get("to")));
	}

	@GetMapping("/api/production-batches/{production_batch}")
	@RequiresPermission("production_batch.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "production_batch", table = TABLE, model = MODEL) Row batch) {
		return DataResponse.of(resource.batch(batch));
	}

	// ---- yazma -------------------------------------------------------------

	/** Cevap 200 (Laravel fresh() ile yeni bir model doner). */
	@PostMapping("/api/production-batches")
	@RequiresPermission("production_batch.create")
	@Transactional
	DataResponse<Map<String, Object>> store(RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, ProductionBatchRequests.store());
		return DataResponse.of(resource.batch(batches.byId(batches.create(v, user))));
	}

	@PatchMapping("/api/production-batches/{production_batch}")
	@RequiresPermission("production_batch.update")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "production_batch", table = TABLE, model = MODEL) Row batch,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionBatchRequests.update(batch.id()));
		batches.update(batch, v);
		return DataResponse.of(resource.batch(batches.byId(batch.id())));
	}

	/** Cevap 204. */
	@DeleteMapping("/api/production-batches/{production_batch}")
	@RequiresPermission("production_batch.delete")
	@Transactional
	ResponseEntity<Void> destroy(@Bound(value = "production_batch", table = TABLE, model = MODEL) Row batch) {
		batches.delete(batch);
		return ResponseEntity.noContent().build();
	}

}
