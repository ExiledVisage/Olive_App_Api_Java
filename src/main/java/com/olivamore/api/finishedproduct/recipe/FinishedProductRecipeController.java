package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.finishedproduct.RecipeResponse;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Dolum tarifleri ve versiyonlari. */
@RestController
class FinishedProductRecipeController {

	private final FinishedProductRecipeService recipes;
	private final RecipeResources resources;

	FinishedProductRecipeController(FinishedProductRecipeService recipes, RecipeResources resources) {
		this.recipes = recipes;
		this.resources = resources;
	}

	/** Aktif tarifler, en yeni once. */
	@GetMapping("/api/finished-product-recipes")
	@PreAuthorize("hasAuthority('finished_product_recipe.view')")
	ListResponse<RecipeResponse> index(@RequestParam(required = false) String name) {
		return new ListResponse<>(resources.responses(recipes.active(name), true));
	}

	@GetMapping("/api/finished-product-recipes/{id}")
	@PreAuthorize("hasAuthority('finished_product_recipe.view')")
	DataResponse<RecipeResponse> show(@PathVariable long id) {
		return DataResponse.of(detail(id));
	}

	/** Ayni addaki butun versiyonlar; yalnizca giris ister. */
	@GetMapping("/api/finished-product-recipes/{id}/versions")
	ListResponse<RecipeResponse> versions(@PathVariable long id) {
		return new ListResponse<>(resources.responses(recipes.versions(id), true));
	}

	@PostMapping("/api/finished-product-recipes")
	@PreAuthorize("hasAuthority('finished_product_recipe.manage')")
	ResponseEntity<DataResponse<RecipeResponse>> store(@Valid @RequestBody CreateRecipeRequest request) {
		return DataResponse.created(detail(recipes.create(request)));
	}

	/** Yeni versiyon olusturur: 201. */
	@RequestMapping(path = "/api/finished-product-recipes/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@PreAuthorize("hasAuthority('finished_product_recipe.manage')")
	ResponseEntity<DataResponse<RecipeResponse>> update(@PathVariable long id,
		@Valid @RequestBody UpdateRecipeRequest request) {
		return DataResponse.created(detail(recipes.update(id, request)));
	}

	@DeleteMapping("/api/finished-product-recipes/{id}")
	@PreAuthorize("hasAuthority('finished_product_recipe.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		recipes.delete(id);
		return Map.of("message", "Tarif başarıyla silindi.");
	}

	/** Icerik ve attribute kurallari yuklu. */
	private RecipeResponse detail(long id) {
		return resources.responses(List.of(recipes.get(id)), true).getFirst();
	}

}
