package com.olivamore.api.finishedproduct;

import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipe;
import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipeRepository;
import com.olivamore.api.finishedproduct.recipe.RecipeAttributeRule;
import com.olivamore.api.finishedproduct.recipe.RecipeAttributeRuleRepository;
import com.olivamore.api.finishedproduct.recipe.RecipeContent;
import com.olivamore.api.finishedproduct.recipe.RecipeContentRepository;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Tarif cevaplari: tarif, urun ve kosu ekranlari ile musterinin formul profili. */
@Component
public class RecipeResources {

	private record NamedRow(Long id, String name) {
	}

	private record LabelRow(Long id, String label) {
	}

	private final FinishedProductRecipeRepository recipes;
	private final RecipeContentRepository contents;
	private final RecipeAttributeRuleRepository rules;
	private final JdbcClient jdbc;

	RecipeResources(FinishedProductRecipeRepository recipes, RecipeContentRepository contents,
		RecipeAttributeRuleRepository rules, JdbcClient jdbc) {
		this.recipes = recipes;
		this.contents = contents;
		this.rules = rules;
		this.jdbc = jdbc;
	}

	/** @param withRules attribute kurallari yuklu mu; degilse {@code attribute_rules} anahtari yok */
	public List<RecipeResponse> responses(List<FinishedProductRecipe> rows, boolean withRules) {
		List<Long> ids = rows.stream().map(FinishedProductRecipe::getId).toList();
		Map<Long, List<RecipeResponse.Content>> contentsByRecipe = contentsOf(ids);
		Map<Long, List<RecipeResponse.AttributeRule>> rulesByRecipe = withRules ? rulesOf(ids) : Map.of();
		return rows.stream()
			.map(recipe -> new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getVersion(), recipe.isActive(),
				recipe.getDescription(), contentsByRecipe.getOrDefault(recipe.getId(), List.of()),
				withRules ? rulesByRecipe.getOrDefault(recipe.getId(), List.of()) : null,
				recipe.getCreatedAt(), recipe.getUpdatedAt()))
			.toList();
	}

	/** Icerikleriyle, attribute kurallari olmadan; bulunamayan id'ler haritada yok. */
	@Transactional(readOnly = true)
	public Map<Long, RecipeResponse> byIds(List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		Map<Long, RecipeResponse> out = new LinkedHashMap<>();
		responses(recipes.findAllById(present), false).forEach(recipe -> out.put(recipe.id(), recipe));
		return out;
	}

	/** Tarif id'sine gore icerikler. */
	public Map<Long, List<RecipeResponse.Content>> contentsOf(Collection<Long> recipeIds) {
		List<Long> present = ValidationErrors.present(recipeIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		List<RecipeContent> rows = contents.findByRecipeIdInOrderByIdAsc(present);
		Map<Long, String> typeNames = names("production_product_types",
			rows.stream().map(RecipeContent::getProductionProductTypeId).toList());
		return rows.stream().collect(Collectors.groupingBy(RecipeContent::getRecipeId, Collectors.mapping(
			row -> new RecipeResponse.Content(row.getId(), row.getProductionProductTypeId(),
				typeNames.get(row.getProductionProductTypeId()), row.getRatio()),
			Collectors.toList())));
	}

	private Map<Long, List<RecipeResponse.AttributeRule>> rulesOf(List<Long> recipeIds) {
		if (recipeIds.isEmpty()) {
			return Map.of();
		}
		List<RecipeAttributeRule> rows = rules.findByRecipeIdInOrderByIdAsc(recipeIds);
		Map<Long, String> labels = labels(rows.stream().map(RecipeAttributeRule::getAttributeDefinitionId).toList());
		return rows.stream().collect(Collectors.groupingBy(RecipeAttributeRule::getRecipeId, Collectors.mapping(
			row -> new RecipeResponse.AttributeRule(row.getId(), row.getAttributeDefinitionId(),
				labels.get(row.getAttributeDefinitionId()), row.getValue(), row.getMinDeviation(), row.getMaxDeviation()),
			Collectors.toList())));
	}

	private Map<Long, String> names(String table, List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name from " + table + " where id in (:ids)").param("ids", present)
			.query(NamedRow.class).list().stream()
			.collect(Collectors.toMap(NamedRow::id, NamedRow::name, (a, b) -> a));
	}

	private Map<Long, String> labels(List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, label from attribute_definitions where id in (:ids)").param("ids", present)
			.query(LabelRow.class).list().stream()
			.collect(Collectors.toMap(LabelRow::id, LabelRow::label, (a, b) -> a));
	}

}
