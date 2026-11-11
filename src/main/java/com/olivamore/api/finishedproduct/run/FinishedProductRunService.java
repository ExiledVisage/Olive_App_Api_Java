package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.finishedproduct.product.FinishedProduct;
import com.olivamore.api.finishedproduct.product.FinishedProductComponent;
import com.olivamore.api.finishedproduct.product.FinishedProductComponentRepository;
import com.olivamore.api.finishedproduct.product.FinishedProductRepository;
import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipe;
import com.olivamore.api.finishedproduct.recipe.RecipeAttributeRule;
import com.olivamore.api.finishedproduct.recipe.RecipeAttributeRuleRepository;
import com.olivamore.api.finishedproduct.recipe.RecipeContent;
import com.olivamore.api.finishedproduct.recipe.RecipeContentRepository;
import com.olivamore.api.finishedproduct.run.RunQueries.Material;
import com.olivamore.api.finishedproduct.run.RunQueries.MaterialAttribute;
import com.olivamore.api.finishedproduct.run.RunQueries.ProductType;
import com.olivamore.api.finishedproduct.run.RunQueries.Unit;
import com.olivamore.api.finishedproduct.support.Quantities;
import com.olivamore.api.inventory.MixShares;
import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.packaging.PackagingAllocations;
import com.olivamore.api.packaging.PackagingAllocations.Need;
import com.olivamore.api.packaging.PackagingAllocations.Selection;
import com.olivamore.api.product.UnitConversion;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Dolum kosusunun planlanmasi (tank ve tarif kontrolleri, materyal payi, tahmini attribute degerleri, ambalaj
 * tahsisi), durum gecisleri ve tamamlama (ambalaj ve yag tuketimi).
 */
@Service
class FinishedProductRunService {

	private static final BigDecimal RATIO_TOLERANCE = new BigDecimal("0.02");
	/** Dolumda cekilen yagin sise icerigini en fazla bu oranda gecmesine izin verilir (hat ici fire). */
	private static final BigDecimal FILL_LOSS_TOLERANCE = new BigDecimal("0.02");
	private static final BigDecimal EPSILON = new BigDecimal("0.00005");
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final MathContext PRECISION = MathContext.DECIMAL128;

	private final FinishedProductRunRepository runs;
	private final RunMaterialRepository runMaterials;
	private final RunAttributeValueRepository attributeValues;
	private final FinishedProductRepository products;
	private final FinishedProductComponentRepository components;
	private final RecipeContentRepository contents;
	private final RecipeAttributeRuleRepository rules;
	private final RunQueries queries;
	private final PackagingAllocations packaging;
	private final StockOperations stock;

	FinishedProductRunService(FinishedProductRunRepository runs, RunMaterialRepository runMaterials,
		RunAttributeValueRepository attributeValues, FinishedProductRepository products,
		FinishedProductComponentRepository components, RecipeContentRepository contents,
			RecipeAttributeRuleRepository rules,
		RunQueries queries, PackagingAllocations packaging, StockOperations stock) {
		this.runs = runs;
		this.runMaterials = runMaterials;
		this.attributeValues = attributeValues;
		this.products = products;
		this.components = components;
		this.contents = contents;
		this.rules = rules;
		this.queries = queries;
		this.packaging = packaging;
		this.stock = stock;
	}

	// ---- planlama --------------------------------------------------------------

	/** Tank ve tarif kontrolleri, materyal paylari, tahmini attribute degerleri, FIFO ambalaj tahsisi. */
	@Transactional
	long create(CreateRunRequest request) {
		ValidationErrors errors = new ValidationErrors();
		FinishedProduct product = products.findWithRecipeById(request.finishedProductId()).orElse(null);
		if (product == null) {
			errors.add("finished_product_id", MessageKeys.EXISTS);
		}
		if (!queries.unitExists(request.source().storageUnitId())) {
			errors.add("source.storage_unit_id", MessageKeys.EXISTS);
		}
		errors.throwIfAny();

		FinishedProductRecipe recipe = product.getRecipe();
		List<RecipeContent> recipeContents = contents.findByRecipeIdOrderByIdAsc(recipe.getId());
		if (recipeContents.isEmpty()) {
			throw new BusinessRuleException("'" + product.getName()
				+ "' ürününün geçerli bir formülü yok; formülsüz dolum yapılamaz.");
		}
		long unitId = request.source().storageUnitId();
		Unit unit = queries.unit(unitId).orElseThrow(() -> new ModelNotFoundException("StorageUnit", unitId));
		Tank tank = loadTank(unitId);
		if (tank.materials().isEmpty()) {
			throw new BusinessRuleException("'" + unit.code() + "' tankı boş.");
		}
		int bottles = request.quantity();
		BigDecimal required = requiredSourceQuantity(product, bottles, BigDecimal.ZERO, tank);
		BigDecimal requested = request.source().quantity() != null ? request.source().quantity()
			: requiredSourceQuantity(product, bottles,
				request.source().lossPercent() == null ? BigDecimal.ZERO : request.source().lossPercent(), tank);

		assertSufficientQuantity(tank, requested);
		assertTankMatchesRecipe(tank, recipe, recipeContents);
		assertSourceQuantityMatchesBottles(product, bottles, requested, required, tank);

		FinishedProductRun run = runs.save(new FinishedProductRun(product, recipe, unitId, bottles, request.note()));
		createRunMaterials(run.getId(), tank, requested);
		createEstimatedAttributeValues(run.getId(), tank, recipe);
		packaging.allocateForRun(run.getId(), needs(product.getId(), bottles));
		return run.getId();
	}

	/**
	 * Tanktaki aktif materyaller ve urun tipleri. Miktarlar ortak birimde (ilk materyalin birimi) karsilastirilir:
	 * her materyal tipinin yogunluguyla cevrilir ({@link MixShares}, tank sayfasindaki cikisla ayni kural).
	 */
	private record Tank(List<Material> materials, Map<Long, ProductType> types) {

		String unit() {
			return materials.getFirst().unit();
		}

		List<MixShares.Part> parts() {
			return materials.stream().map(m -> {
				ProductType type = type(m);
				return new MixShares.Part(m.quantity(), m.unit(), type == null ? null : type.effectiveDensity(),
					type == null ? null : type.name());
			}).toList();
		}

		/** Materyallerin ortak birimdeki miktarlari (materials sirasiyla). */
		List<BigDecimal> common() {
			return MixShares.inUnit(parts(), unit());
		}

		BigDecimal total() {
			return MixShares.total(parts(), unit());
		}

		ProductType type(Material material) {
			Long id = material.productionProductTypeId();
			return id == null ? null : types.get(id);
		}

	}

	private Tank loadTank(long unitId) {
		List<Material> materials = queries.activeInTank(unitId);
		return new Tank(materials,
			queries.productTypes(materials.stream().map(Material::productionProductTypeId).toList()));
	}

	private static void assertSufficientQuantity(Tank tank, BigDecimal requested) {
		BigDecimal total = tank.total();
		if (requested.compareTo(total) > 0) {
			throw new BusinessRuleException("Tankta yeterli miktar yok. Mevcut: " + Quantities.plain(total) + ", İstenen: "
				+ Quantities.plain(requested) + ".");
		}
	}

	/** Tarifte olmayan urun tolerans ustunde olmamali; her icerik beklenen oranin +-%2'sinde olmali. */
	private void assertTankMatchesRecipe(Tank tank, FinishedProductRecipe recipe,
		List<RecipeContent> recipeContents) {
		BigDecimal total = tank.total();
		List<BigDecimal> common = tank.common();
		Set<Long> recipeTypes = recipeContents.stream().map(RecipeContent::getProductionProductTypeId)
			.collect(Collectors.toSet());
		List<Material> foreign = tank.materials().stream()
			.filter(m -> !recipeTypes.contains(m.productionProductTypeId())).toList();
		BigDecimal limit = RATIO_TOLERANCE.add(EPSILON);
		if (!foreign.isEmpty() && total.signum() > 0) {
			BigDecimal foreignQuantity = sumOf(tank, common, m -> !recipeTypes.contains(m.productionProductTypeId()));
			BigDecimal ratio = foreignQuantity.divide(total, PRECISION);
			if (ratio.compareTo(limit) > 0) {
				Set<String> names = new LinkedHashSet<>();
				for (Material m : foreign) {
					ProductType type = tank.type(m);
					names.add(type != null ? type.name() : "ID:" + m.productionProductTypeId());
				}
				throw new BusinessRuleException("Tankta tarifte olmayan ürün var: " + String.join(", ", names) + " (%"
					+ percent(ratio) + "). '" + recipe.getName() + "' tarifi bu tanktan doldurulamaz.");
			}
		}
		Map<Long, ProductType> types = queries.productTypes(
			recipeContents.stream().map(RecipeContent::getProductionProductTypeId).toList());
		for (RecipeContent content : recipeContents) {
			long typeId = content.getProductionProductTypeId();
			BigDecimal expected = content.getRatio();
			BigDecimal actual = sumOf(tank, common, m -> Objects.equals(m.productionProductTypeId(), typeId));
			BigDecimal actualRatio = total.signum() > 0 ? actual.divide(total, PRECISION) : BigDecimal.ZERO;
			if (actualRatio.subtract(expected).abs().compareTo(limit) > 0) {
				ProductType type = types.get(typeId);
				String name = type != null ? type.name() : "ID:" + typeId;
				throw new BusinessRuleException("Tank blend oranı tarife uymuyor. '" + name + "' için beklenen oran: "
					+ percent(expected) + "%, tankta bulunan: " + percent(actualRatio) + "%.");
			}
		}
	}

	/** Kosulu saglayan materyallerin ortak birimdeki toplami. */
	private static BigDecimal sumOf(Tank tank, List<BigDecimal> common, Predicate<Material> filter) {
		BigDecimal sum = BigDecimal.ZERO;
		for (int i = 0; i < tank.materials().size(); i++) {
			if (filter.test(tank.materials().get(i))) {
				sum = sum.add(common.get(i));
			}
		}
		return sum;
	}

	/** Oranin yuzde karsiligi, tek basamak, gereksiz sifirsiz. */
	private static String percent(BigDecimal ratio) {
		return Quantities.plain(ratio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP));
	}

	/** Tanktan cekilecek miktar, tankin (ilk materyalin) biriminde: adet x hacim litre, fire payiyla, 4 basamak. */
	private static BigDecimal requiredSourceQuantity(FinishedProduct product, int bottles, BigDecimal lossPercent,
		Tank tank) {
		BigDecimal litres = BigDecimal.valueOf(bottles).multiply(product.getVolumeLitre())
			.multiply(BigDecimal.ONE.add(lossPercent.divide(HUNDRED, PRECISION)));
		Material first = tank.materials().getFirst();
		return convert(litres, "LITRE", first.unit(), tank.type(first)).setScale(Quantities.SCALE, RoundingMode.HALF_UP);
	}

	/** Ayni birimse oldugu gibi, yoksa tipin yogunluguyla. */
	private static BigDecimal convert(BigDecimal quantity, String from, String to, ProductType type) {
		return UnitConversion.convert(quantity, from, to, type == null ? null : type.effectiveDensity(),
			type == null ? null : type.name());
	}

	private static String label(String unit) {
		return switch (unit) {
			case "LITRE" -> "L";
			case "KG" -> "kg";
			case null -> "null";
			default -> unit;
		};
	}

	/** Sise adedi x hacim, tanktan cekilecek miktarla uyusmali (eksik olamaz, fazlasi %2 fireyi asamaz). */
	private static void assertSourceQuantityMatchesBottles(FinishedProduct product, int bottles, BigDecimal source,
		BigDecimal required, Tank tank) {
		String unit = label(Objects.requireNonNullElse(tank.materials().getFirst().unit(), "LITRE"));
		String head = bottles + " adet × " + Quantities.plain(product.getVolumeLitre()) + " L = "
			+ Quantities.plain(required) + " " + unit + " yağ ";
		if (source.add(EPSILON).compareTo(required) < 0) {
			throw new BusinessRuleException(head + "gerekiyor, tanktan çekilecek miktar " + Quantities.plain(source) + " "
				+ unit + ".");
		}
		BigDecimal max = required.multiply(BigDecimal.ONE.add(FILL_LOSS_TOLERANCE));
		if (source.compareTo(max.add(EPSILON)) > 0) {
			throw new BusinessRuleException(head + "yeterli, tanktan çekilecek miktar " + Quantities.plain(source) + " "
				+ unit + ". Fark, %" + Quantities.plain(FILL_LOSS_TOLERANCE.multiply(HUNDRED))
				+ " fire toleransını aşıyor (en fazla "
				+ Quantities.plain(max.setScale(Quantities.SCALE, RoundingMode.HALF_UP)) + " " + unit + ").");
		}
	}

	/**
	 * Her materyalden tanktaki payi oraninda ({@link MixShares}: ortak birimde oran, materyalin kendi biriminde pay,
	 * yuvarlama farki son materyale); sifir pay yazilmaz.
	 */
	private void createRunMaterials(long runId, Tank tank, BigDecimal requested) {
		List<BigDecimal> used = MixShares.split(tank.parts(), requested, tank.unit());
		for (int i = 0; i < tank.materials().size(); i++) {
			if (used.get(i).signum() > 0) {
				runMaterials.save(new RunMaterial(runId, tank.materials().get(i).id(), used.get(i)));
			}
		}
	}

	/**
	 * Tarifin her attribute kurali icin tanktaki materyallerin (ortak birimde) agirlikli ortalamasi; kabul
	 * araliginin disindaysa kosu planlanmaz. Degeri olmayan kural atlanir.
	 */
	private void createEstimatedAttributeValues(long runId, Tank tank, FinishedProductRecipe recipe) {
		List<RecipeAttributeRule> attributeRules = rules.findByRecipeIdOrderByIdAsc(recipe.getId());
		Map<Long, String> labels = queries.attributeLabels(
			attributeRules.stream().map(RecipeAttributeRule::getAttributeDefinitionId).toList());
		List<MaterialAttribute> attributes = queries.materialAttributes(
			tank.materials().stream().map(Material::id).toList());
		List<BigDecimal> common = tank.common();
		for (RecipeAttributeRule rule : attributeRules) {
			long definitionId = rule.getAttributeDefinitionId();
			BigDecimal weightedSum = BigDecimal.ZERO;
			BigDecimal weightedTotal = BigDecimal.ZERO;
			for (int i = 0; i < tank.materials().size(); i++) {
				Material material = tank.materials().get(i);
				BigDecimal value = attributes.stream()
					.filter(a -> Objects.equals(a.materialId(), material.id())
						&& Objects.equals(a.attributeDefinitionId(), definitionId))
					.map(MaterialAttribute::valueDecimal)
					.findFirst().orElse(null);
				if (value == null) {
					continue;
				}
				weightedSum = weightedSum.add(value.multiply(common.get(i)));
				weightedTotal = weightedTotal.add(common.get(i));
			}
			if (weightedTotal.signum() <= 0) {
				continue;
			}
			BigDecimal estimated = weightedSum.divide(weightedTotal, PRECISION)
				.setScale(Quantities.SCALE, RoundingMode.HALF_UP);
			BigDecimal min = rule.getMinDeviation() == null ? null : rule.getValue().subtract(rule.getMinDeviation());
			BigDecimal max = rule.getMaxDeviation() == null ? null : rule.getValue().add(rule.getMaxDeviation());
			String label = Objects.requireNonNullElse(labels.get(definitionId), "");
			if (min != null && estimated.compareTo(min) < 0) {
				throw new BusinessRuleException("Tahmini " + label + " değeri (" + Quantities.plain(estimated) + ") "
					+ "kabul aralığının altında. Min: " + Quantities.plain(min) + ".");
			}
			if (max != null && estimated.compareTo(max) > 0) {
				throw new BusinessRuleException("Tahmini " + label + " değeri (" + Quantities.plain(estimated) + ") "
					+ "kabul aralığının üstünde. Max: " + Quantities.plain(max) + ".");
			}
			attributeValues.save(new RunAttributeValue(runId, definitionId, estimated));
		}
	}

	/** Urunun ambalaj bilesenleri: kalem basina kosu adedi x bilesen adedi. */
	private List<Need> needs(long productId, int bottles) {
		return components.findByFinishedProductIdOrderByIdAsc(productId).stream()
			.map((FinishedProductComponent c) -> new Need(c.getPackagingItemId(), (long) bottles * c.getQuantity()))
			.toList();
	}

	// ---- durum -------------------------------------------------------------------

	@Transactional
	void updateStatus(long id, UpdateRunStatusRequest request, long userId) {
		FinishedProductRun run = find(id);
		RunStatus current = run.getStatus();
		RunStatus status = request.status();
		if (!current.transitions().contains(status)) {
			throw new BusinessRuleException("'" + current.value() + "' durumundan '" + status.value()
				+ "' durumuna geçiş yapılamaz.");
		}
		switch (status) {
			case IN_PROGRESS -> run.start();
			case COMPLETED -> complete(run, userId);
			default -> run.cancel();
		}
	}

	/**
	 * Once her sey dogrulanir (ambalaj, tanktaki materyaller), sonra stok degisir: urun stogu, ambalaj
	 * lotlari, tanktan cikis.
	 */
	private void complete(FinishedProductRun run, long userId) {
		long productId = run.getProduct().getId();
		int bottles = run.getQuantity();
		List<Need> needs = needs(productId, bottles);
		List<RunMaterial> materialRows = runMaterials.findByRunIdOrderByIdAsc(run.getId());
		Map<Long, Material> materials =
			queries.materials(materialRows.stream().map(RunMaterial::getSourceMaterialId).toList());

		assertPackagingAvailable(run.getId(), needs);
		assertRunMaterialsStillAvailable(materialRows, materials);

		products.addStock(productId, bottles, Timestamps.now());
		packaging.consumeForRun(run.getId(), needs, userId);
		for (RunMaterial row : materialRows) {
			Material material = materials.get(row.getSourceMaterialId());
			stock.outboundForRun(material.currentLocationId(), material.id(), row.getQuantityUsed(),
				"Dolum Run #" + run.getId(), run.getId());
		}
		run.complete();
	}

	/** Kalem basina eldeki (aktif lot) adet yeter mi; tahsis varsa hala gecerli mi. */
	private void assertPackagingAvailable(long runId, List<Need> needs) {
		List<String> shortages = new ArrayList<>();
		for (Need need : needs) {
			long available = packaging.availableQuantity(need.itemId());
			if (available < need.quantity()) {
				shortages.add(queries.packagingItemName(need.itemId()) + " (gerekli " + need.quantity() + ", lotlarda "
					+ available + ")");
			}
		}
		if (!shortages.isEmpty()) {
			throw new BusinessRuleException("Ambalaj stoğu yetersiz: " + String.join("; ", shortages) + ".");
		}
		if (queries.hasAllocations(runId)) {
			packaging.assertAllocationStillAvailable(runId, needs);
		}
	}

	/** Planlamadan sonra tanktaki materyal tasinmis, tuketilmis ya da azalmis olabilir. */
	private void assertRunMaterialsStillAvailable(List<RunMaterial> rows, Map<Long, Material> materials) {
		Map<Long, ProductType> types = queries.productTypes(
			materials.values().stream().map(Material::productionProductTypeId).toList());
		List<String> problems = new ArrayList<>();
		for (RunMaterial row : rows) {
			Material material = materials.get(row.getSourceMaterialId());
			if (material == null) {
				problems.add("materyal #" + row.getSourceMaterialId() + " bulunamadı");
				continue;
			}
			ProductType type = material.productionProductTypeId() == null
				? null : types.get(material.productionProductTypeId());
			String label = (type != null && type.name() != null ? type.name() : "materyal") + " #" + material.id();
			if (!"active".equals(material.status())) {
				problems.add(label + " artık aktif değil (" + material.status() + ")");
				continue;
			}
			if (!"storage_unit".equals(material.currentLocationType()) || material.currentLocationId() == null
				|| material.currentLocationId() == 0) {
				problems.add(label + " artık bir tankta değil");
				continue;
			}
			BigDecimal quantity = Quantities.normalize(material.quantity());
			if (quantity.add(EPSILON).compareTo(row.getQuantityUsed()) < 0) {
				problems.add(label + " içinde " + quantity.toPlainString() + " kaldı, "
					+ Quantities.plain(row.getQuantityUsed()) + " gerekiyor");
			}
		}
		if (!problems.isEmpty()) {
			throw new BusinessRuleException("Koşu planlandıktan sonra tank içeriği değişmiş, tamamlanamıyor: "
				+ String.join("; ", problems) + ". Koşuyu iptal edip yeniden planlayın.");
		}
	}

	// ---- attribute degeri, tahsis, silme ------------------------------------------------

	/** Gercek attribute degeri; deger baska kosununsa 404. */
	@Transactional
	AttributeValueResponse updateAttributeValue(long runId, long valueId, UpdateAttributeValueRequest request) {
		FinishedProductRun run = find(runId);
		RunAttributeValue value = attributeValues.findById(valueId)
			.orElseThrow(() -> new ModelNotFoundException("FinishedProductRunAttributeValue", valueId));
		if (!Objects.equals(value.getRunId(), run.getId())) {
			throw new HttpStatusException(404, "Bu attribute değeri bu run'a ait değil.");
		}
		value.recordActual(request.actualValue());
		return new AttributeValueResponse(value.getId(), value.getEstimatedValue(), value.getActualValue());
	}

	/** Liste tahsisin tamami. */
	@Transactional
	void syncPackagings(long id, SyncRunPackagingsRequest request) {
		FinishedProductRun run = find(id);
		List<Long> lotIds = request.packagings().stream()
			.map(SyncRunPackagingsRequest.Packaging::packagingLotId).toList();
		ValidationErrors errors = new ValidationErrors();
		errors.distinct("packagings", "packaging_lot_id", lotIds);
		errors.exists("packagings", "packaging_lot_id", lotIds, queries.existingLots(lotIds));
		errors.throwIfAny();
		packaging.syncRunAllocation(run.getId(), run.getStatus().value(),
			needs(run.getProduct().getId(), run.getQuantity()),
			request.packagings().stream().map(p -> new Selection(p.packagingLotId(), p.quantity())).toList());
	}

	/** Yalnizca planli kosu silinir. */
	@Transactional
	void delete(long id) {
		FinishedProductRun run = find(id);
		if (run.getStatus() != RunStatus.PLANNED) {
			throw new BusinessRuleException("Sadece planlanmış (planned) durumdaki run silinebilir.");
		}
		runs.delete(run);
	}

	private FinishedProductRun find(long id) {
		return runs.findById(id).orElseThrow(() -> new ModelNotFoundException("FinishedProductRun", id));
	}

}
