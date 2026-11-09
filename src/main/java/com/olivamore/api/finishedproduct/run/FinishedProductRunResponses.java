package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.finishedproduct.RecipeResponse;
import com.olivamore.api.finishedproduct.product.FinishedProduct;
import com.olivamore.api.finishedproduct.run.FinishedProductRunResponse.AttributeValue;
import com.olivamore.api.finishedproduct.run.FinishedProductRunResponse.Packaging;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entity'den API cevabina; hangi iliskilerin yuklu oldugu ekrana gore. */
@Component
class FinishedProductRunResponses {

	/**
	 * @param recipeContents tarifin icerigi yuklu mu
	 * @param sourceUnit     tank yuklu mu
	 * @param details        materyaller, ambalaj tahsisi ve attribute degerleri yuklu mu
	 */
	private record Shape(boolean recipeContents, boolean sourceUnit, boolean details) {
	}

	/** Liste: urun, tarif, tank. */
	private static final Shape LIST = new Shape(false, true, false);
	/** Detay: her sey, tarifin icerigi dahil. */
	private static final Shape DETAIL = new Shape(true, true, true);
	/** Ekleme ve durum cevabi: tank ve tarif icerigi yok. */
	private static final Shape WRITTEN = new Shape(false, false, true);

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"));

	private final FinishedProductRunRepository runs;
	private final RecipeResources recipes;
	private final RunQueries queries;

	FinishedProductRunResponses(FinishedProductRunRepository runs, RecipeResources recipes, RunQueries queries) {
		this.runs = runs;
		this.recipes = recipes;
		this.queries = queries;
	}

	/** En yeni once. */
	@Transactional(readOnly = true)
	List<FinishedProductRunResponse> list() {
		return responses(runs.findAllByOrderByCreatedAtDesc(), LIST);
	}

	@Transactional(readOnly = true)
	Page<FinishedProductRunResponse> page(int page, int size) {
		Page<FinishedProductRun> rows = runs.findAll(PageRequest.of(page - 1, size, NEWEST_FIRST));
		Map<Long, FinishedProductRunResponse> responses = responses(rows.getContent(), LIST).stream()
			.collect(Collectors.toMap(FinishedProductRunResponse::id, Function.identity()));
		return rows.map(run -> responses.get(run.getId()));
	}

	@Transactional(readOnly = true)
	FinishedProductRunResponse detail(long id) {
		return one(id, DETAIL);
	}

	@Transactional(readOnly = true)
	FinishedProductRunResponse written(long id) {
		return one(id, WRITTEN);
	}

	private FinishedProductRunResponse one(long id, Shape shape) {
		FinishedProductRun run = runs.findWithDetailsById(id)
			.orElseThrow(() -> new ModelNotFoundException("FinishedProductRun", id));
		return responses(List.of(run), shape).getFirst();
	}

	private List<FinishedProductRunResponse> responses(List<FinishedProductRun> rows, Shape shape) {
		List<Long> ids = rows.stream().map(FinishedProductRun::getId).toList();
		Map<Long, List<RecipeResponse.Content>> contents = shape.recipeContents()
			? recipes.contentsOf(rows.stream().map(run -> run.getRecipe().getId()).toList())
			: Map.of();
		Map<Long, RunQueries.Unit> units = shape.sourceUnit()
			? queries.units(rows.stream().map(FinishedProductRun::getSourceStorageUnitId).toList())
			: Map.of();
		Map<Long, List<FinishedProductRunResponse.Material>> materials = shape.details() ? materials(ids) : Map.of();
		Map<Long, List<Packaging>> packagings = shape.details() ? packagings(ids) : Map.of();
		Map<Long, List<AttributeValue>> values = shape.details() ? values(ids) : Map.of();
		return rows.stream().map(run -> {
			FinishedProduct product = run.getProduct();
			Long recipeId = run.getRecipe().getId();
			return new FinishedProductRunResponse(run.getId(), run.getQuantity(), run.getStatus(),
				run.getStatus().transitions(), run.getNote(), run.getStartedAt(), run.getCompletedAt(),
				new FinishedProductRunResponse.Product(product.getId(), product.getName(), product.getVolumeLitre()),
				new RecipeResponse.Summary(recipeId, run.getRecipe().getName(), run.getRecipe().getVersion(),
					shape.recipeContents() ? contents.getOrDefault(recipeId, List.of()) : null),
				shape.sourceUnit() ? Optional.ofNullable(unit(units, run)) : null,
				shape.details() ? materials.getOrDefault(run.getId(), List.of()) : null,
				shape.details() ? packagings.getOrDefault(run.getId(), List.of()) : null,
				shape.details() ? values.getOrDefault(run.getId(), List.of()) : null,
				run.getCreatedAt(), run.getUpdatedAt());
		}).toList();
	}

	private static FinishedProductRunResponse.Unit unit(Map<Long, RunQueries.Unit> units, FinishedProductRun run) {
		RunQueries.Unit unit = run.getSourceStorageUnitId() == null ? null : units.get(run.getSourceStorageUnitId());
		return unit == null ? null : new FinishedProductRunResponse.Unit(unit.id(), unit.name(), unit.code());
	}

	private Map<Long, List<FinishedProductRunResponse.Material>> materials(List<Long> runIds) {
		return queries.materialDisplays(runIds).stream().collect(Collectors.groupingBy(RunQueries.MaterialDisplay::runId,
			Collectors.mapping(row -> new FinishedProductRunResponse.Material(row.id(), row.sourceMaterialId(),
				row.quantityUsed(), row.unit(), row.productionProductTypeId(), row.typeName(),
				row.typeName() == null ? null : RunQueries.effective(row.densityKgPerLitre(), row.defaultDensityKgPerLitre())),
				Collectors.toList())));
	}

	private Map<Long, List<Packaging>> packagings(List<Long> runIds) {
		return queries.packagingDisplays(runIds).stream()
			.collect(Collectors.groupingBy(RunQueries.PackagingDisplay::runId,
			Collectors.mapping(row -> new Packaging(row.id(), row.packagingItemId(), row.packagingItemName(),
				row.packagingItemType(), row.packagingLotId(), row.lotCode(), row.lotReceivedAt(), row.lotRemaining(),
				row.supplierName(), row.quantity()), Collectors.toList())));
	}

	private Map<Long, List<AttributeValue>> values(List<Long> runIds) {
		return queries.valueDisplays(runIds).stream().collect(Collectors.groupingBy(RunQueries.ValueDisplay::runId,
			Collectors.mapping(row -> new AttributeValue(row.id(), row.attributeDefinitionId(), row.attributeLabel(),
				row.estimatedValue(), row.actualValue()), Collectors.toList())));
	}

}
