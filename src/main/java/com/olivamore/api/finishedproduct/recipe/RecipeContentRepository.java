package com.olivamore.api.finishedproduct.recipe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecipeContentRepository extends JpaRepository<RecipeContent, Long> {

	List<RecipeContent> findByRecipeIdInOrderByIdAsc(Collection<Long> recipeIds);

	List<RecipeContent> findByRecipeIdOrderByIdAsc(long recipeId);

}
