package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.finishedproduct.product.FinishedProductRepository;
import com.olivamore.api.finishedproduct.run.FinishedProductRunRepository;
import com.olivamore.api.finishedproduct.support.Quantities;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Dolum tarifleri. Guncelleme yeni versiyon olusturur, eskisi pasife alinir ve urunler yeni versiyona tasinir.
 */
@Service
class FinishedProductRecipeService {

	private final FinishedProductRecipeRepository recipes;
	private final RecipeContentRepository contents;
	private final RecipeAttributeRuleRepository rules;
	private final FinishedProductRepository products;
	private final FinishedProductRunRepository runs;
	private final ProductDirectory catalog;

	FinishedProductRecipeService(FinishedProductRecipeRepository recipes, RecipeContentRepository contents,
		RecipeAttributeRuleRepository rules, FinishedProductRepository products, FinishedProductRunRepository runs,
		ProductDirectory catalog) {
		this.recipes = recipes;
		this.contents = contents;
		this.rules = rules;
		this.products = products;
		this.runs = runs;
		this.catalog = catalog;
	}

	/** Aktif tarifler, en yeni once; {@code name} (bos ya da "0" degilse) adinda gecenler. */
	@Transactional(readOnly = true)
	List<FinishedProductRecipe> active(String name) {
		return name != null && !name.isEmpty() && !"0".equals(name)
			? recipes.search(name)
			: recipes.findByActiveTrueOrderByCreatedAtDescIdDesc();
	}

	/** Ayni addaki butun versiyonlar, yeniden eskiye. */
	@Transactional(readOnly = true)
	List<FinishedProductRecipe> versions(long id) {
		return recipes.findByNameOrderByVersionDesc(find(id).getName());
	}

	@Transactional(readOnly = true)
	FinishedProductRecipe get(long id) {
		return find(id);
	}

	/** Versiyon 1, aktif. */
	@Transactional
	long create(CreateRecipeRequest request) {
		validateLines(request.contents(), request.attributeRules(), true);
		FinishedProductRecipe recipe = recipes.save(new FinishedProductRecipe(request.name(), request.description(), 1));
		saveContents(recipe.getId(), request.contents());
		saveRules(recipe.getId(), request.attributeRules());
		return recipe.getId();
	}

	/**
	 * Yeni versiyon. Yalnizca aktif versiyon guncellenir; verilmeyen icerik ve kurallar eski versiyondan kopyalanir.
	 * @return yeni versiyonun id'si
	 */
	@Transactional
	long update(long id, UpdateRecipeRequest request) {
		FinishedProductRecipe recipe = find(id);
		validateLines(request.contents(), request.attributeRules(), request.contents() != null);
		if (!recipe.isActive()) {
			FinishedProductRecipe latest = recipes.findFirstByNameAndActiveTrue(recipe.getName()).orElse(null);
			throw new BusinessRuleException("Sadece aktif tarif versiyonu güncellenebilir. '" + recipe.getName() + "' v"
				+ recipe.getVersion() + " pasif"
				+ (latest != null ? "; güncel versiyon v" + latest.getVersion() + " (id " + latest.getId() + ")." : "."));
		}
		recipe.deactivate();
		String name = request.name() != null ? request.name() : recipe.getName();
		int next = recipes.maxVersion(name) + 1;
		String description = request.description() != null ? request.description().value() : recipe.getDescription();
		FinishedProductRecipe version = recipes.save(new FinishedProductRecipe(name, description,
			Math.max(next, recipe.getVersion() + 1)));
		if (request.contents() != null) {
			saveContents(version.getId(), request.contents());
		}
		else {
			contents.findByRecipeIdOrderByIdAsc(id).forEach(content -> contents.save(new RecipeContent(version.getId(),
				content.getProductionProductTypeId(), content.getRatio())));
		}
		if (request.attributeRules() != null) {
			saveRules(version.getId(), request.attributeRules());
		}
		else {
			rules.findByRecipeIdOrderByIdAsc(id).forEach(rule -> rules.save(new RecipeAttributeRule(version.getId(),
				rule.getAttributeDefinitionId(), rule.getValue(), rule.getMinDeviation(), rule.getMaxDeviation())));
		}
		products.moveToRecipe(id, version, Timestamps.now());
		return version.getId();
	}

	@Transactional
	void delete(long id) {
		FinishedProductRecipe recipe = find(id);
		if (runs.existsByRecipeId(id)) {
			throw new BusinessRuleException("'" + recipe.getName() + "' v" + recipe.getVersion()
				+ " ile yapılmış dolum koşuları var; silinemez.");
		}
		if (products.existsByRecipeId(id)) {
			throw new BusinessRuleException("Bu tarif bir son ürüne bağlı olduğu için silinemez.");
		}
		recipes.delete(recipe);
	}

	/** Tekrar eden ve bulunmayan tip ve attribute id'leri; icerik verildiyse oran toplami 1 olmali. */
	private void validateLines(List<? extends RecipeLines.Content> lines, List<? extends RecipeLines.Rule> ruleLines,
		boolean checkTotal) {
		ValidationErrors errors = new ValidationErrors();
		if (lines != null) {
			List<Long> typeIds = lines.stream().map(RecipeLines.Content::productionProductTypeId).toList();
			errors.distinct("contents", "production_product_type_id", typeIds);
			errors.exists("contents", "production_product_type_id", typeIds, catalog.existingTypes(typeIds));
		}
		if (ruleLines != null) {
			List<Long> definitionIds = ruleLines.stream().map(RecipeLines.Rule::attributeDefinitionId).toList();
			errors.distinct("attribute_rules", "attribute_definition_id", definitionIds);
			errors.exists("attribute_rules", "attribute_definition_id", definitionIds,
				catalog.existingAttributeDefinitions(definitionIds));
		}
		if (checkTotal) {
			BigDecimal total = lines.stream().map(RecipeLines.Content::ratio).reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(Quantities.SCALE, RoundingMode.HALF_UP);
			if (total.compareTo(BigDecimal.ONE) != 0) {
				errors.add("contents", "İçerik oranlarının toplamı 1.00 olmalıdır. Mevcut toplam: " + Quantities.plain(total));
			}
		}
		errors.throwIfAny();
	}

	private void saveContents(long recipeId, List<? extends RecipeLines.Content> lines) {
		lines.forEach(line -> contents.save(new RecipeContent(recipeId, line.productionProductTypeId(), line.ratio())));
	}

	private void saveRules(long recipeId, List<? extends RecipeLines.Rule> lines) {
		if (lines != null) {
			lines.forEach(line -> rules.save(new RecipeAttributeRule(recipeId, line.attributeDefinitionId(), line.value(),
				line.minDeviation(), line.maxDeviation())));
		}
	}

	private FinishedProductRecipe find(long id) {
		return recipes.findById(id).orElseThrow(() -> new ModelNotFoundException("FinishedProductRecipe", id));
	}

}
