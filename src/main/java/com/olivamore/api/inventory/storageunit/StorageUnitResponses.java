package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialQueries;
import com.olivamore.api.inventory.material.MaterialResponses;
import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.inventory.material.Quantities;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.product.UnitConversion;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.olivamore.api.inventory.material.MaterialResponses.lookup;

/**
 * Tank cevaplari: iliskiler (izinli urun/tip, kurallar, bakiyeler, aktif materyaller) ve kapasite metrikleri
 * (kapasite biriminde, kg ve litre).
 */
@Component
class StorageUnitResponses {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private record LocatedMaterial(Long id, Long currentLocationId, Long productionProductTypeId, BigDecimal quantity,
		MaterialUnit unit) {
	}

	private final StorageUnitRepository units;
	private final StorageUnitBalanceRepository balances;
	private final StorageUnitAllowedProductRepository allowedProducts;
	private final StorageUnitAllowedTypeRepository allowedTypes;
	private final StorageUnitAttributeRuleRepository rules;
	private final MaterialQueries queries;
	private final MaterialResponses materials;
	private final JdbcClient jdbc;
	private final ProductDirectory directory;

	StorageUnitResponses(StorageUnitRepository units, StorageUnitBalanceRepository balances,
		StorageUnitAllowedProductRepository allowedProducts, StorageUnitAllowedTypeRepository allowedTypes,
		StorageUnitAttributeRuleRepository rules, MaterialQueries queries, MaterialResponses materials,
		JdbcClient jdbc, ProductDirectory directory) {
		this.units = units;
		this.balances = balances;
		this.allowedProducts = allowedProducts;
		this.allowedTypes = allowedTypes;
		this.rules = rules;
		this.queries = queries;
		this.materials = materials;
		this.jdbc = jdbc;
		this.directory = directory;
	}

	/** En yeni once. */
	List<StorageUnitResponse> all() {
		return responses(units.findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
	}

	StorageUnitResponse one(long id) {
		return responses(List.of(units.findById(id).orElseThrow())).getFirst();
	}

	List<StorageUnitResponse.Balance> balances(long unitId) {
		List<StorageUnitBalance> rows = balances.findByStorageUnitIdOrderByIdAsc(unitId);
		Map<Long, MaterialQueries.Product> products = queries.products(
			rows.stream().map(StorageUnitBalance::getProductionProductId).toList());
		return rows.stream().map(b -> balance(b, products)).toList();
	}

	private List<StorageUnitResponse> responses(List<StorageUnit> rows) {
		List<Long> ids = rows.stream().map(StorageUnit::getId).toList();
		if (ids.isEmpty()) {
			return List.of();
		}
		List<StorageUnitAllowedProduct> productRows = allowedProducts.findByStorageUnitIdInOrderByIdAsc(ids);
		List<StorageUnitAllowedType> typeRows = allowedTypes.findByStorageUnitIdInOrderByIdAsc(ids);
		Map<Long, List<StorageUnitResponse.AllowedProduct>> products = allowedProducts(productRows);
		Map<Long, List<StorageUnitResponse.AllowedType>> types = allowedTypes(typeRows);
		Map<Long, List<StorageUnitResponse.AttributeRule>> attributeRules = attributeRules(ids);
		Map<Long, List<StorageUnitBalance>> balanceRows = new LinkedHashMap<>();
		balances.findByStorageUnitIdInOrderByIdAsc(ids).forEach(
			b -> balanceRows.computeIfAbsent(b.getStorageUnitId(), k -> new ArrayList<>()).add(b));
		Map<Long, MaterialQueries.Product> balanceProducts = queries.products(balanceRows.values().stream()
			.flatMap(List::stream).map(StorageUnitBalance::getProductionProductId).toList());
		List<LocatedMaterial> located = locatedMaterials(ids);
		Map<Long, List<MaterialResponse>> current = currentMaterials(located);
		Map<Long, List<LocatedMaterial>> contents = new LinkedHashMap<>();
		located.forEach(m -> contents.computeIfAbsent(m.currentLocationId(), k -> new ArrayList<>()).add(m));
		Densities densities = densities(productRows, typeRows, located);

		List<StorageUnitResponse> out = new ArrayList<>();
		for (StorageUnit u : rows) {
			List<StorageUnitBalance> unitBalances = balanceRows.getOrDefault(u.getId(), List.of());
			Metrics metrics = Metrics.of(u, contents.getOrDefault(u.getId(), List.of()), densities);
			out.add(new StorageUnitResponse(u.getId(), u.getName(), u.getCode(), u.getCapacity(), u.getCapacityUnit(),
				u.getKind(), u.isBlendTank(), u.isAllowMultipleTypes(), u.isAllowMultipleProducts(), u.getDescription(),
				u.isActive(), metrics.filled(), metrics.remaining(), metrics.filledPercentage(),
				metrics.emptyPercentage(), metrics.capacityKg(), metrics.capacityLitre(), metrics.filledKg(),
				metrics.filledLitre(),
				products.getOrDefault(u.getId(), List.of()), types.getOrDefault(u.getId(), List.of()),
				attributeRules.getOrDefault(u.getId(), List.of()),
				unitBalances.stream().map(b -> balance(b, balanceProducts)).toList(),
				current.getOrDefault(u.getId(), List.of()), u.getCreatedAt(), u.getUpdatedAt()));
		}
		return out;
	}

	/**
	 * Tiplerin etkin yogunluklari ve bos tankta kapasitenin diger birimdeki karsiligi icin yogunluk.
	 * @param emptyTank tank id'sine gore; belirlenemiyorsa haritada yok
	 */
	private record Densities(Map<Long, BigDecimal> byType, Map<Long, BigDecimal> emptyTank) {

		BigDecimal of(Long typeId) {
			return typeId == null ? null : byType.get(typeId);
		}

	}

	/**
	 * Doluluk tankin kapasite biriminde: her materyal kendi tipinin yogunluguyla cevrilir, cevrilemeyen (yogunluk
	 * yok) sayilmaz. Kg ve litre karsiliklari: cevrilemeyen materyal varsa null. Kapasitenin diger birimdeki
	 * karsiligi: doluysa karisimin yogunlugundan (toplam kg / toplam litre), bossa izinli urunlerden. 2 basamak,
	 * yarim deger sifirdan uzaga.
	 */
	private record Metrics(Number filled, Number remaining, Number filledPercentage, Number emptyPercentage,
		Number capacityKg, Number capacityLitre, Number filledKg, Number filledLitre) {

		static Metrics of(StorageUnit unit, List<LocatedMaterial> contents, Densities densities) {
			String capacityUnit = unit.getCapacityUnit().value();
			BigDecimal filled = BigDecimal.ZERO;
			for (LocatedMaterial m : contents) {
				BigDecimal converted = UnitConversion.convertOrNull(m.quantity(), m.unit().value(), capacityUnit,
					densities.of(m.productionProductTypeId()));
				filled = converted == null ? filled : filled.add(converted);
			}
			BigDecimal capacity = unit.getCapacity();
			BigDecimal remaining = capacity.subtract(filled).max(BigDecimal.ZERO);
			BigDecimal percentage = capacity.signum() > 0
				? filled.multiply(HUNDRED).divide(capacity, Quantities.BALANCE_SCALE, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;
			BigDecimal empty = HUNDRED.subtract(percentage).max(BigDecimal.ZERO);

			BigDecimal filledKg = total(contents, UnitConversion.KG, densities);
			BigDecimal filledLitre = total(contents, UnitConversion.LITRE, densities);
			BigDecimal other = otherCapacity(unit, contents.isEmpty(), densities, filledKg, filledLitre);
			boolean inKg = unit.getCapacityUnit() == MaterialUnit.KG;
			return new Metrics(Quantities.number(Quantities.balance(filled)),
				Quantities.number(Quantities.balance(remaining)), Quantities.number(percentage),
				Quantities.number(empty), number(inKg ? capacity : other), number(inKg ? other : capacity),
				number(filledKg), number(filledLitre));
		}

		/** Icerigin verilen birimdeki toplami; cevrilemeyen materyal varsa null. */
		private static BigDecimal total(List<LocatedMaterial> contents, String unit, Densities densities) {
			BigDecimal total = BigDecimal.ZERO;
			for (LocatedMaterial m : contents) {
				BigDecimal converted = UnitConversion.convertOrNull(m.quantity(), m.unit().value(), unit,
					densities.of(m.productionProductTypeId()));
				if (converted == null) {
					return null;
				}
				total = total.add(converted);
			}
			return total;
		}

		private static BigDecimal otherCapacity(StorageUnit unit, boolean emptyTank, Densities densities,
			BigDecimal filledKg, BigDecimal filledLitre) {
			boolean inKg = unit.getCapacityUnit() == MaterialUnit.KG;
			if (emptyTank) {
				return UnitConversion.convertOrNull(unit.getCapacity(), unit.getCapacityUnit().value(),
					inKg ? UnitConversion.LITRE : UnitConversion.KG, densities.emptyTank().get(unit.getId()));
			}
			if (filledKg == null || filledLitre == null || filledKg.signum() == 0 || filledLitre.signum() == 0) {
				return null;
			}
			BigDecimal ratio = inKg ? filledLitre.divide(filledKg, MathContext.DECIMAL128)
				: filledKg.divide(filledLitre, MathContext.DECIMAL128);
			return unit.getCapacity().multiply(ratio);
		}

		private static Number number(BigDecimal value) {
			return value == null ? null : Quantities.number(Quantities.balance(value));
		}

	}

	/**
	 * Icerikteki tiplerin etkin yogunluklari. Bos tankin yogunlugu: aday tipler (izinli tipler, yoksa izinli
	 * kategorilerin tipleri) tek bir kategoriye aitse o kategorinin varsayilan yogunlugu; tipler farkli
	 * kategorilerden geliyorsa, varsayilan yoksa ya da aday yoksa belirlenemez.
	 */
	private Densities densities(List<StorageUnitAllowedProduct> productRows, List<StorageUnitAllowedType> typeRows,
		List<LocatedMaterial> located) {
		Map<Long, List<Long>> candidates = new LinkedHashMap<>();
		typeRows.forEach(t -> candidates.computeIfAbsent(t.getStorageUnitId(), k -> new ArrayList<>())
			.add(t.getProductionProductTypeId()));
		Map<Long, List<Long>> productsOfUnit = new LinkedHashMap<>();
		productRows.stream().filter(p -> !candidates.containsKey(p.getStorageUnitId()))
			.forEach(p -> productsOfUnit.computeIfAbsent(p.getStorageUnitId(), k -> new ArrayList<>())
				.add(p.getProductionProductId()));
		Map<Long, ProductDirectory.ProductType> productTypes = directory.productTypes(
			directory.typeIdsOfProducts(productsOfUnit.values().stream().flatMap(List::stream).distinct().toList()));
		productsOfUnit.forEach((unitId, productIds) -> candidates.put(unitId, productTypes.values().stream()
			.filter(t -> productIds.contains(t.productionProductId())).map(ProductDirectory.ProductType::id).toList()));

		Set<Long> typeIds = new HashSet<>(productTypes.keySet());
		located.forEach(m -> typeIds.add(m.productionProductTypeId()));
		candidates.values().forEach(typeIds::addAll);
		Map<Long, ProductDirectory.ProductType> types = directory.productTypes(typeIds);
		Map<Long, BigDecimal> byType = new LinkedHashMap<>();
		types.values().stream().filter(t -> t.effectiveDensity() != null)
			.forEach(t -> byType.put(t.id(), t.effectiveDensity()));

		Map<Long, BigDecimal> emptyTank = new LinkedHashMap<>();
		candidates.forEach((unitId, ids) -> {
			Set<ProductDirectory.ProductType> found = new HashSet<>();
			ids.stream().map(types::get).filter(Objects::nonNull).forEach(found::add);
			Set<Long> products = new HashSet<>();
			found.forEach(t -> products.add(t.productionProductId()));
			BigDecimal density = products.size() == 1 && found.size() == ids.size()
				? UnitConversion.effectiveDensity(null, found.iterator().next().defaultDensityKgPerLitre()) : null;
			if (density != null) {
				emptyTank.put(unitId, density);
			}
		});
		return new Densities(byType, emptyTank);
	}

	private Map<Long, List<StorageUnitResponse.AllowedProduct>> allowedProducts(List<StorageUnitAllowedProduct> rows) {
		Map<Long, MaterialQueries.Product> products = queries.products(
			rows.stream().map(StorageUnitAllowedProduct::getProductionProductId).toList());
		Map<Long, List<StorageUnitResponse.AllowedProduct>> out = new LinkedHashMap<>();
		for (StorageUnitAllowedProduct a : rows) {
			out.computeIfAbsent(a.getStorageUnitId(), k -> new ArrayList<>()).add(new StorageUnitResponse.AllowedProduct(
				a.getId(), a.getStorageUnitId(), a.getProductionProductId(),
				product(lookup(products, a.getProductionProductId())), a.getCreatedAt(), a.getUpdatedAt()));
		}
		return out;
	}

	private Map<Long, List<StorageUnitResponse.AllowedType>> allowedTypes(List<StorageUnitAllowedType> rows) {
		Map<Long, MaterialQueries.ProductType> types = queries.productTypes(
			rows.stream().map(StorageUnitAllowedType::getProductionProductTypeId).toList());
		Map<Long, List<StorageUnitResponse.AllowedType>> out = new LinkedHashMap<>();
		for (StorageUnitAllowedType a : rows) {
			MaterialQueries.ProductType t = lookup(types, a.getProductionProductTypeId());
			StorageUnitResponse.TypeSummary type = t == null ? null : new StorageUnitResponse.TypeSummary(t.id(),
				t.productionProductId(), t.name(), t.code(), t.type(), t.unit(), null);
			out.computeIfAbsent(a.getStorageUnitId(), k -> new ArrayList<>()).add(new StorageUnitResponse.AllowedType(
				a.getId(), a.getStorageUnitId(), a.getProductionProductTypeId(), type, a.getCreatedAt(), a.getUpdatedAt()));
		}
		return out;
	}

	private Map<Long, List<StorageUnitResponse.AttributeRule>> attributeRules(List<Long> unitIds) {
		List<StorageUnitAttributeRule> rows = rules.findByStorageUnitIdInOrderByIdAsc(unitIds);
		Map<Long, MaterialResponse.Definition> definitions = materials.definitions(
			rows.stream().map(StorageUnitAttributeRule::getDefinitionId).toList());
		Map<Long, List<StorageUnitResponse.AttributeRule>> out = new LinkedHashMap<>();
		for (StorageUnitAttributeRule a : rows) {
			out.computeIfAbsent(a.getStorageUnitId(), k -> new ArrayList<>()).add(new StorageUnitResponse.AttributeRule(
				a.getId(), a.getStorageUnitId(), a.getDefinitionId(), a.getMinValueDecimal(), a.getMaxValueDecimal(),
				a.getApplyMode(), a.isActive(), a.getNote(), lookup(definitions, a.getDefinitionId()), a.getCreatedAt(),
				a.getUpdatedAt()));
		}
		return out;
	}

	/** Tanklardaki aktif materyaller. */
	private List<LocatedMaterial> locatedMaterials(List<Long> unitIds) {
		return jdbc.sql("""
			select id, current_location_id, production_product_type_id, quantity, unit from materials
			where current_location_id in (:ids) and current_location_type = 'storage_unit' and status = 'active'
			order by id
			""").param("ids", unitIds).query(LocatedMaterial.class).list();
	}

	/** Aktif materyallerin cevaplari, tanka gore gruplu. */
	private Map<Long, List<MaterialResponse>> currentMaterials(List<LocatedMaterial> rows) {
		List<MaterialResponse> responses = materials.materials(rows.stream().map(LocatedMaterial::id).toList(), false);
		Map<Long, List<MaterialResponse>> out = new LinkedHashMap<>();
		for (int i = 0; i < rows.size(); i++) {
			out.computeIfAbsent(rows.get(i).currentLocationId(), k -> new ArrayList<>()).add(responses.get(i));
		}
		return out;
	}

	private static StorageUnitResponse.Balance balance(StorageUnitBalance b,
		Map<Long, MaterialQueries.Product> products) {
		return new StorageUnitResponse.Balance(b.getId(), b.getStorageUnitId(), b.getProductionProductId(),
			b.getCurrentQuantity(), b.getUnit(), product(lookup(products, b.getProductionProductId())), b.getCreatedAt(),
			b.getUpdatedAt());
	}

	private static StorageUnitResponse.Product product(MaterialQueries.Product p) {
		return p == null ? null : new StorageUnitResponse.Product(p.id(), p.key(), p.name(), p.description());
	}

}
