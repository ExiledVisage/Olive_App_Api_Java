package com.olivamore.api.finishedproduct;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.packaging.PackagingAllocations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Laravel {@code FinishedProductRunController}: dolum kosulari. */
@RestController
class RunController {

	private final Db db;
	private final LaravelValidator validator;
	private final RunFlow flow;
	private final RunReads reads;
	private final PackagingAllocations packaging;

	RunController(Db db, LaravelValidator validator, RunFlow flow, RunReads reads, PackagingAllocations packaging) {
		this.db = db;
		this.validator = validator;
		this.flow = flow;
		this.reads = reads;
		this.packaging = packaging;
	}

	/** En yeni once; {@code per_page} ile sayfali. */
	@GetMapping("/api/finished-product-runs")
	@RequiresPermission("finished_product_run.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		String order = " order by created_at desc";
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(reads.runs(db.select("select * from finished_product_runs" + order), RunReads.LIST));
		}
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate from finished_product_runs");
		List<Row> rows = total == 0 ? List.of() : db.select("select * from finished_product_runs" + order
			+ " limit " + perPage.get() + " offset " + (long) (page - 1) * perPage.get());
		return LaravelPaginator.paginate(reads.runs(rows, RunReads.LIST), total, page, perPage.get(), request);
	}

	@GetMapping("/api/finished-product-runs/{run}")
	@RequiresPermission("finished_product_run.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run) {
		return DataResponse.of(reads.one(run, RunReads.DETAIL));
	}

	/** Planlama (201): tank ve tarif kontrolleri, materyal paylari, tahmini attribute degerleri, FIFO ambalaj tahsisi. */
	@PostMapping("/api/finished-product-runs")
	@RequiresPermission("finished_product_run.create")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("finished_product_id", "required|integer|exists:finished_products,id")
			.field("quantity", "required|integer|min:1")
			.field("note", "nullable|string")
			.field("source", "required|array")
			.field("source.storage_unit_id", "required|integer|exists:storage_units,id")
			.field("source.quantity", "nullable|numeric|min:0.001")
			.field("source.loss_percent", "nullable|numeric|min:0|max:2")
			.message("source.loss_percent.max", "Fire payı en fazla %2 olabilir."));
		Map<?, ?> source = (Map<?, ?>) v.get("source");
		long runId = flow.create(Php.intval(v.get("finished_product_id")), v.get("quantity"), v.get("note"),
			Php.intval(source.get("storage_unit_id")), source.get("quantity"), source.get("loss_percent"));
		Map<String, Object> body = reads.one(db.find("finished_product_runs", runId).orElseThrow(), RunReads.WRITTEN);
		// Laravel yeni modeli dondurur: adet istekteki haliyle
		body.put("quantity", v.get("quantity"));
		return DataResponse.created(body);
	}

	@PatchMapping("/api/finished-product-runs/{run}/status")
	@RequiresPermission("finished_product_run.complete")
	@Transactional
	DataResponse<Map<String, Object>> updateStatus(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("status", "required|in:planned,in_progress,completed,cancelled"));
		flow.updateStatus(run, Php.strval(v.get("status")), user.id());
		return DataResponse.of(reads.one(db.find("finished_product_runs", run.id()).orElseThrow(), RunReads.WRITTEN));
	}

	/** Gercek attribute degeri; deger baska kosununsa 404. */
	@PatchMapping("/api/finished-product-runs/{run}/attribute-values/{attributeValue}")
	@RequiresPermission("finished_product_run.complete")
	@Transactional
	Map<String, Object> updateAttributeValue(
		@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run,
		@Bound(value = "attributeValue", table = "finished_product_run_attribute_values", model = "FinishedProductRunAttributeValue") Row value,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of().field("actual_value", "required|numeric"));
		if (Php.intval(value.get("finished_product_run_id")) != run.id()) {
			throw new HttpStatusException(404, "Bu attribute değeri bu run'a ait değil.");
		}
		// decimal:4 cast'i: 4 basamakta esit deger degisiklik sayilmaz
		db.update("finished_product_run_attribute_values", value.id(), Map.of("actual_value", Casts.decimal(v.get("actual_value"), 4)));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", value.get("id"));
		body.put("estimated_value", Casts.decimal(value.get("estimated_value"), 4));
		body.put("actual_value", Casts.decimal(db.find("finished_product_run_attribute_values", value.id()).orElseThrow().get("actual_value"), 4));
		return body;
	}

	/** Ambalaj lotu tahsisinin elle degistirilmesi; liste tahsisin tamami. */
	@PutMapping("/api/finished-product-runs/{run}/packagings")
	@RequiresPermission("finished_product_run.create")
	@Transactional
	DataResponse<Map<String, Object>> syncPackagings(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("packagings", "required|array|min:1")
			.field("packagings.*.packaging_lot_id", "required|integer|distinct|exists:packaging_lots,id")
			.field("packagings.*.quantity", "required|integer|min:1"));
		packaging.syncRunAllocation(run.id(), run.string("status"),
			flow.needs(run.longValue("finished_product_id"), Php.intval(run.get("quantity"))), RecipeController.items(v.get("packagings")));
		return DataResponse.of(reads.one(db.find("finished_product_runs", run.id()).orElseThrow(), RunReads.DETAIL));
	}

	@DeleteMapping("/api/finished-product-runs/{run}")
	@RequiresPermission("finished_product_run.create")
	@Transactional
	Map<String, String> destroy(@Bound(value = "run", table = "finished_product_runs", model = "FinishedProductRun") Row run) {
		if (!"planned".equals(run.string("status"))) {
			throw new BusinessRuleException("Sadece planlanmış (planned) durumdaki run silinebilir.");
		}
		db.delete("finished_product_runs", run.id());
		return Map.of("message", "Run başarıyla silindi.");
	}

}
