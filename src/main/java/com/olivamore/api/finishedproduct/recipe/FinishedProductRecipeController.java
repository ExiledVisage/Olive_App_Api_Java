package com.olivamore.api.finishedproduct.recipe;

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
import com.olivamore.api.finishedproduct.RecipeResources;

/** Laravel {@code FinishedProductRecipeController}: dolum tarifleri ve versiyonlari. */
@RestController
class FinishedProductRecipeController {

	private final LaravelValidator validator;
	private final FinishedProductRecipeService recipes;
	private final RecipeResources resources;

	FinishedProductRecipeController(LaravelValidator validator, FinishedProductRecipeService recipes, RecipeResources resources) {
		this.validator = validator;
		this.recipes = recipes;
		this.resources = resources;
	}

	/** Aktif tarifler, en yeni once; {@code ?name=}. */
	@GetMapping("/api/finished-product-recipes")
	@RequiresPermission("finished_product_recipe.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		return new ListResponse.Plain<>(resources.recipes(recipes.active(request.getParameter("name")), true));
	}

	@GetMapping("/api/finished-product-recipes/{recipe}")
	@RequiresPermission("finished_product_recipe.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		return DataResponse.of(detail(recipe.id()));
	}

	/** Ayni addaki butun versiyonlar. Laravel'de izin grubunun disinda: yalnizca giris. */
	@GetMapping("/api/finished-product-recipes/{recipe}/versions")
	ListResponse<Map<String, Object>> versions(@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		return new ListResponse.Plain<>(resources.recipes(recipes.versions(recipe), true));
	}

	/** 201: versiyon 1, aktif. */
	@PostMapping("/api/finished-product-recipes")
	@RequiresPermission("finished_product_recipe.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductRecipeRequests.store(), FinishedProductRecipeRequests.storeAfter());
		return DataResponse.created(detail(recipes.create(v)));
	}

	/** Yeni versiyon (201; Laravel'de yeni olusan model). */
	@RequestMapping(path = "/api/finished-product-recipes/{recipe}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("finished_product_recipe.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> update(
		@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe, RequestInput input) {
		ValidatedInput v = validator.validate(input, FinishedProductRecipeRequests.update(), FinishedProductRecipeRequests.updateAfter());
		return DataResponse.created(detail(recipes.update(recipe, v)));
	}

	@DeleteMapping("/api/finished-product-recipes/{recipe}")
	@RequiresPermission("finished_product_recipe.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "recipe", table = "finished_product_recipes", model = "FinishedProductRecipe") Row recipe) {
		recipes.delete(recipe);
		return Map.of("message", "Tarif başarıyla silindi.");
	}

	/** Icerik ve attribute kurallari yuklu. */
	private Map<String, Object> detail(long id) {
		return resources.recipes(List.of(recipes.byId(id)), true).getFirst();
	}

}
