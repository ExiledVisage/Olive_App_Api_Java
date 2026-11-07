package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.Listing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/** Laravel {@code FinishedProductRunController}: dolum kosulari. */
@RestController
class FinishedProductRunController {

	private final LaravelValidator validator;
	private final FinishedProductRunService runs;
	private final FinishedProductRunResource resource;

	FinishedProductRunController(LaravelValidator validator, FinishedProductRunService runs, FinishedProductRunResource resource) {
		this.validator = validator;
		this.runs = runs;
		this.resource = resource;
	}

	/** En yeni once; {@code per_page} ile sayfali. */
	@GetMapping("/api/finished-product-runs")
	@RequiresPermission("finished_product_run.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(resource.runs(runs.all(), FinishedProductRunResource.LIST));
		}
		int page = Listing.currentPage(request);
		long total = runs.count();
		List<Row> rows = total == 0 ? List.of() : runs.page(page, perPage.get());
		return LaravelPaginator.paginate(resource.runs(rows, FinishedProductRunResource.LIST), total, page, perPage.get(), request);
	}

	@GetMapping("/api/finished-product-runs/{run}")
	@RequiresPermission("finished_product_run.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run) {
		return DataResponse.of(resource.one(run, FinishedProductRunResource.DETAIL));
	}

	/** Planlama (201): tank ve tarif kontrolleri, materyal paylari, tahmini attribute degerleri, FIFO ambalaj tahsisi. */
	@PostMapping("/api/finished-product-runs")
	@RequiresPermission("finished_product_run.create")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductRunRequests.store());
		Map<?, ?> source = (Map<?, ?>) v.get("source");
		long runId = runs.create(Php.intval(v.get("finished_product_id")), v.get("quantity"), v.get("note"),
			Php.intval(source.get("storage_unit_id")), source.get("quantity"), source.get("loss_percent"));
		Map<String, Object> body = resource.one(runs.byId(runId), FinishedProductRunResource.WRITTEN);
		// Laravel yeni modeli dondurur: adet istekteki haliyle
		body.put("quantity", v.get("quantity"));
		return DataResponse.created(body);
	}

	@PatchMapping("/api/finished-product-runs/{run}/status")
	@RequiresPermission("finished_product_run.complete")
	@Transactional
	DataResponse<Map<String, Object>> updateStatus(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, FinishedProductRunRequests.updateStatus());
		runs.updateStatus(run, Php.strval(v.get("status")), user.id());
		return DataResponse.of(resource.one(runs.byId(run.id()), FinishedProductRunResource.WRITTEN));
	}

	/** Gercek attribute degeri; deger baska kosununsa 404 (dogrulamadan sonra). */
	@PatchMapping("/api/finished-product-runs/{run}/attribute-values/{attributeValue}")
	@RequiresPermission("finished_product_run.complete")
	@Transactional
	Map<String, Object> updateAttributeValue(
		@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run,
		@Bound(value = "attributeValue", table = "finished_product_run_attribute_values", model = "FinishedProductRunAttributeValue") Row value,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductRunRequests.updateAttributeValue());
		return runs.updateAttributeValue(run, value, v.get("actual_value"));
	}

	/** Ambalaj lotu tahsisinin elle degistirilmesi. */
	@PutMapping("/api/finished-product-runs/{run}/packagings")
	@RequiresPermission("finished_product_run.create")
	@Transactional
	DataResponse<Map<String, Object>> syncPackagings(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductRunRequests.syncPackagings());
		runs.syncPackagings(run, items(v.get("packagings")));
		return DataResponse.of(resource.one(runs.byId(run.id()), FinishedProductRunResource.DETAIL));
	}

	@DeleteMapping("/api/finished-product-runs/{run}")
	@RequiresPermission("finished_product_run.create")
	@Transactional
	Map<String, String> destroy(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run) {
		runs.delete(run);
		return Map.of("message", "Run başarıyla silindi.");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
		if (value instanceof Map<?, ?> m) {
			return new ArrayList<>((Collection<Map<String, Object>>) m.values());
		}
		return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

}
