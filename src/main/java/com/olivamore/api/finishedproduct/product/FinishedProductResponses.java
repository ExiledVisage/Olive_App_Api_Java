package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.finishedproduct.RecipeResources;
import com.olivamore.api.finishedproduct.RecipeResponse;
import com.olivamore.api.finishedproduct.product.FinishedProductResponse.Location;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entity'den API cevabina: depo toplamlari, raf konumlari, ambalaj kalemleri, tarif. */
@Component
class FinishedProductResponses {

	private record ItemRow(Long id, String name, String type) {
	}

	private record TotalRow(Long productId, long total) {
	}

	private record LocationRow(Long productId, Long storageId, String storageName, Long slotId, String slotName,
		long quantity) {
	}

	private static final Comparator<String> NAME_ORDER = FinishedProductResponses::compareNames;

	private final FinishedProductRepository products;
	private final FinishedProductComponentRepository components;
	private final RecipeResources recipes;
	private final JdbcClient jdbc;

	FinishedProductResponses(FinishedProductRepository products, FinishedProductComponentRepository components,
		RecipeResources recipes, JdbcClient jdbc) {
		this.products = products;
		this.components = components;
		this.recipes = recipes;
		this.jdbc = jdbc;
	}

	/** En yeni once; tarif icerigi yok. */
	@Transactional(readOnly = true)
	List<FinishedProductResponse> list(Boolean isActive) {
		List<FinishedProduct> rows = isActive == null ? products.findAllByOrderByCreatedAtDesc()
			: products.findByActiveOrderByCreatedAtDesc(isActive);
		return responses(rows, false, true, true);
	}

	/** Tarif icerigi, bilesenler, raf konumlari ve depo toplamlari. */
	@Transactional(readOnly = true)
	FinishedProductResponse detail(long id) {
		return responses(List.of(find(id)), true, true, true).getFirst();
	}

	/** Ekleme ve guncelleme cevabi: tarif (icerik yok), bilesenler, depo toplami. */
	@Transactional(readOnly = true)
	FinishedProductResponse written(long id) {
		return responses(List.of(find(id)), false, false, false).getFirst();
	}

	private FinishedProduct find(long id) {
		return products.findWithRecipeById(id).orElseThrow(() -> new ModelNotFoundException("FinishedProduct", id));
	}

	/**
	 * @param recipeContents tarifin icerigi yuklu mu
	 * @param awaiting       sevkiyat bekleyen toplam yuklu mu
	 * @param withLocations  raf konumlari yuklu mu
	 */
	private List<FinishedProductResponse> responses(List<FinishedProduct> rows, boolean recipeContents,
		boolean awaiting,
		boolean withLocations) {
		List<Long> ids = rows.stream().map(FinishedProduct::getId).toList();
		Map<Long, List<RecipeResponse.Content>> contents = recipeContents
			? recipes.contentsOf(rows.stream().map(p -> p.getRecipe().getId()).toList())
			: Map.of();
		Map<Long, List<FinishedProductResponse.Component>> componentsByProduct = components(ids);
		Map<Long, Long> stored = stored(ids);
		Map<Long, Long> awaitingTotals = awaiting ? awaiting(ids) : Map.of();
		Map<Long, List<Location>> locations = withLocations ? locations(ids) : Map.of();
		return rows.stream().map(product -> {
			Long id = product.getId();
			long storedStock = stored.getOrDefault(id, 0L);
			Long awaitingStock = awaitingTotals.get(id);
			long open = Math.max(0, product.getTotalStock() - storedStock - (awaitingStock == null ? 0 : awaitingStock));
			RecipeResponse.Summary recipe = new RecipeResponse.Summary(product.getRecipe().getId(),
				product.getRecipe().getName(), product.getRecipe().getVersion(),
				recipeContents ? contents.getOrDefault(product.getRecipe().getId(), List.of()) : null);
			return new FinishedProductResponse(id, product.getName(), product.getVolumeLitre(), product.getTotalStock(),
				storedStock, awaitingStock, open, product.isActive(), recipe,
				componentsByProduct.getOrDefault(id, List.of()),
				withLocations ? locations.getOrDefault(id, List.of()) : null,
				product.getCreatedAt(), product.getUpdatedAt());
		}).toList();
	}

	private Map<Long, List<FinishedProductResponse.Component>> components(List<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		List<FinishedProductComponent> rows = components.findByFinishedProductIdInOrderByIdAsc(productIds);
		Map<Long, ItemRow> items = items(rows.stream().map(FinishedProductComponent::getPackagingItemId).toList());
		return rows.stream().collect(Collectors.groupingBy(FinishedProductComponent::getFinishedProductId,
			Collectors.mapping(row -> {
				ItemRow item = items.get(row.getPackagingItemId());
				return new FinishedProductResponse.Component(row.getId(), row.getPackagingItemId(), item == null
					? null : item.name(),
					item == null ? null : item.type(), row.getQuantity());
			}, Collectors.toList())));
	}

	private Map<Long, ItemRow> items(List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, type from packaging_items where id in (:ids)").param("ids", present)
			.query(ItemRow.class).list().stream().collect(Collectors.toMap(ItemRow::id, Function.identity()));
	}

	private Map<Long, Long> stored(List<Long> ids) {
		return totals("""
			select finished_product_id as product_id, sum(quantity) as total
			from finished_product_storage_stocks where finished_product_id in (:ids)
			group by finished_product_id
			""", ids);
	}

	/** Raftan alinmis, sevk bekleyen adetler. */
	private Map<Long, Long> awaiting(List<Long> ids) {
		return totals("""
			select m.finished_product_id as product_id, sum(m.quantity) as total
			from finished_product_stock_movements m join orders o on o.id = m.order_id
			where m.finished_product_id in (:ids) and m.movement_type = 'order_pick' and o.status = 'ready'
			group by m.finished_product_id
			""", ids);
	}

	private Map<Long, Long> totals(String sql, List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql(sql).param("ids", ids).query(TotalRow.class).list().stream()
			.collect(Collectors.toMap(TotalRow::productId, TotalRow::total));
	}

	/** Depo > raf > adet; depo ve raf adina gore. */
	private Map<Long, List<Location>> locations(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Comparator<LocationRow> order = Comparator
			.comparing(LocationRow::storageName, NAME_ORDER)
			.thenComparing(LocationRow::slotName, NAME_ORDER);
		return jdbc.sql("""
			select s.finished_product_id as product_id, st.id as storage_id, st.name as storage_name,
				sl.id as slot_id, sl.name as slot_name, s.quantity
			from finished_product_storage_slot_stocks s
			join finished_product_storage_slots sl on sl.id = s.finished_product_storage_slot_id
			join finished_product_storages st on st.id = sl.finished_product_storage_id
			where s.finished_product_id in (:ids) and s.quantity > 0
			""").param("ids", ids).query(LocationRow.class).list().stream()
			.sorted(order)
			.collect(Collectors.groupingBy(LocationRow::productId, Collectors.mapping(
				row -> new Location(row.storageId(), row.storageName(), row.slotId(), row.slotName(), row.quantity()),
				Collectors.toList())));
	}

	/** Ikisi de sayisal adsa sayi olarak, degilse harf sirasiyla. */
	private static int compareNames(String left, String right) {
		String a = left == null ? "" : left;
		String b = right == null ? "" : right;
		try {
			return new BigDecimal(a.strip()).compareTo(new BigDecimal(b.strip()));
		}
		catch (NumberFormatException e) {
			return Integer.signum(a.compareTo(b));
		}
	}

}
