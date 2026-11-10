package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipe;
import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipeRepository;
import com.olivamore.api.finishedproduct.run.FinishedProductRunRepository;
import com.olivamore.api.finishedproduct.support.Quantities;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.finishedproduct.OrderDirectory;
import com.olivamore.api.packaging.PackagingDirectory;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Bitmis urunler ve ambalaj bilesenleri. */
@Service
class FinishedProductService {

	private final FinishedProductRepository products;
	private final FinishedProductComponentRepository components;
	private final FinishedProductRecipeRepository recipes;
	private final FinishedProductRunRepository runs;
	private final JdbcClient jdbc;
	private final PackagingDirectory packaging;
	private final OrderDirectory orders;

	FinishedProductService(FinishedProductRepository products, FinishedProductComponentRepository components,
		FinishedProductRecipeRepository recipes, FinishedProductRunRepository runs, JdbcClient jdbc,
		PackagingDirectory packaging, OrderDirectory orders) {
		this.products = products;
		this.components = components;
		this.recipes = recipes;
		this.runs = runs;
		this.jdbc = jdbc;
		this.packaging = packaging;
		this.orders = orders;
	}

	@Transactional
	long create(CreateFinishedProductRequest request) {
		ValidationErrors errors = new ValidationErrors();
		FinishedProductRecipe recipe = recipe(request.finishedProductRecipeId(), errors);
		validateComponents(request.components(), errors);
		errors.throwIfAny();
		FinishedProduct product = products.save(new FinishedProduct(request.name(), recipe, request.volumeLitre(),
			request.isActive()));
		saveComponents(product.getId(), request.components());
		return product.getId();
	}

	/** Fabrikada sisesi olan urunun hacmi degistirilemez. Verilen bilesenler eskilerin yerine gecer. */
	@Transactional
	void update(long id, UpdateFinishedProductRequest request) {
		FinishedProduct product = find(id);
		ValidationErrors errors = new ValidationErrors();
		FinishedProductRecipe recipe = recipe(request.finishedProductRecipeId(), errors);
		if (request.components() != null) {
			validateComponents(request.components(), errors);
		}
		errors.throwIfAny();
		if (request.volumeLitre() != null && Quantities.differ(request.volumeLitre(), product.getVolumeLitre())
			&& product.getTotalStock() > 0) {
			throw new BusinessRuleException("'" + product.getName() + "' ürününün fabrikada " + product.getTotalStock()
				+ " şişesi var; hacmi değiştirilemez. Farklı hacim için yeni ürün tanımlayın.");
		}
		product.apply(request, recipe);
		if (request.components() != null) {
			components.deleteForProduct(id);
			saveComponents(id, request.components());
		}
	}

	/** Dolum kaydi olan urun silinmez; siparis, raf ve depo kaydi olan urun 422 ile kullanim yerlerini soyler. */
	@Transactional
	void delete(long id) {
		FinishedProduct product = find(id);
		if (runs.existsByProductId(id)) {
			throw new BusinessRuleException("Bu son ürüne ait dolum kaydı bulunduğu için silinemez.");
		}
		DeletionGuard.of("Bu son ürün")
			.usedBy("sipariş kalemi", orders.itemCount(id))
			.usedBy("depo hareketi", countBy("finished_product_stock_movements", id))
			.usedBy("raf stoğu", countBy("finished_product_storage_slot_stocks", id))
			.usedBy("depo stoğu", countBy("finished_product_storage_stocks", id))
			.check();
		products.delete(product);
	}

	private FinishedProductRecipe recipe(Long id, ValidationErrors errors) {
		if (id == null) {
			return null;
		}
		FinishedProductRecipe recipe = recipes.findById(id).orElse(null);
		if (recipe == null) {
			errors.add("finished_product_recipe_id", LaravelMessages.EXISTS);
		}
		return recipe;
	}

	private void validateComponents(List<? extends ComponentLine> lines, ValidationErrors errors) {
		List<Long> itemIds = lines.stream().map(ComponentLine::packagingItemId).toList();
		errors.distinct("components", "packaging_item_id", itemIds);
		errors.exists("components", "packaging_item_id", itemIds, packaging.items(itemIds).keySet());
	}

	private void saveComponents(long productId, List<? extends ComponentLine> lines) {
		lines.forEach(line -> components.save(new FinishedProductComponent(productId, line.packagingItemId(),
			line.quantity())));
	}

	private long countBy(String table, long productId) {
		return jdbc.sql("select count(*) from " + table + " where finished_product_id = ?").param(productId)
			.query(Long.class).single();
	}

	private FinishedProduct find(long id) {
		return products.findById(id).orElseThrow(() -> new ModelNotFoundException("FinishedProduct", id));
	}

}
