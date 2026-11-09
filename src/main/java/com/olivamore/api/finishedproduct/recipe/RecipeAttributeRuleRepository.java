package com.olivamore.api.finishedproduct.recipe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecipeAttributeRuleRepository extends JpaRepository<RecipeAttributeRule, Long> {

	List<RecipeAttributeRule> findByRecipeIdInOrderByIdAsc(Collection<Long> recipeIds);

	List<RecipeAttributeRule> findByRecipeIdOrderByIdAsc(long recipeId);

}
