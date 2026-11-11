package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialQueries;
import com.olivamore.api.inventory.material.MaterialResponses;
import com.olivamore.api.inventory.material.Quantities;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.inventory.material.MaterialResponses.lookup;

/** Tank cevaplari: iliskiler (izinli urun/tip, kurallar, bakiyeler, aktif materyaller) ve kapasite metrikleri. */
@Component
class StorageUnitResponses {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private record LocatedMaterial(Long id, Long currentLocationId) {
	}

	private final StorageUnitRepository units;
	private final StorageUnitBalanceRepository balances;
	private final StorageUnitAllowedProductRepository allowedProducts;
	private final StorageUnitAllowedTypeRepository allowedTypes;
	private final StorageUnitAttributeRuleRepository rules;
	private final MaterialQueries queries;
	private final MaterialResponses materials;
	private final JdbcClient jdbc;

	StorageUnitResponses(StorageUnitRepository units, StorageUnitBalanceRepository balances,
		StorageUnitAllowedProductRepository allowedProducts, StorageUnitAllowedTypeRepository allowedTypes,
		StorageUnitAttributeRuleRepository rules, MaterialQueries queries, MaterialResponses materials,
		JdbcClient jdbc) {
		this.units = units;
		this.balances = balances;
		this.allowedProducts = allowedProducts;
		this.allowedTypes = allowedTypes;
		this.rules = rules;
		this.queries = queries;
		this.materials = materials;
		this.jdbc = jdbc;
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
		Map<Long, List<StorageUnitResponse.AllowedProduct>> products = allowedProducts(ids);
		Map<Long, List<StorageUnitResponse.AllowedType>> types = allowedTypes(ids);
		Map<Long, List<StorageUnitResponse.AttributeRule>> attributeRules = attributeRules(ids);
		Map<Long, List<StorageUnitBalance>> balanceRows = new LinkedHashMap<>();
		balances.findByStorageUnitIdInOrderByIdAsc(ids).forEach(
			b -> balanceRows.computeIfAbsent(b.getStorageUnitId(), k -> new ArrayList<>()).add(b));
		Map<Long, MaterialQueries.Product> balanceProducts = queries.products(balanceRows.values().stream()
			.flatMap(List::stream).map(StorageUnitBalance::getProductionProductId).toList());
		Map<Long, List<MaterialResponse>> current = currentMaterials(ids);

		List<StorageUnitResponse> out = new ArrayList<>();
		for (StorageUnit u : rows) {
			List<StorageUnitBalance> unitBalances = balanceRows.getOrDefault(u.getId(), List.of());
			Metrics metrics = Metrics.of(u, unitBalances);
			out.add(new StorageUnitResponse(u.getId(), u.getName(), u.getCode(), u.getCapacity(), u.getCapacityUnit(),
				u.isBlendTank(), u.isAllowMultipleTypes(), u.isAllowMultipleProducts(), u.getDescription(), u.isActive(),
				metrics.filled(), metrics.remaining(), metrics.filledPercentage(), metrics.emptyPercentage(),
				products.getOrDefault(u.getId(), List.of()), types.getOrDefault(u.getId(), List.of()),
				attributeRules.getOrDefault(u.getId(), List.of()),
				unitBalances.stream().map(b -> balance(b, balanceProducts)).toList(),
				current.getOrDefault(u.getId(), List.of()), u.getCreatedAt(), u.getUpdatedAt()));
		}
		return out;
	}

	/** Doluluk yalnizca tankin kapasite birimindeki bakiyelerden; 2 basamak, yarim deger sifirdan uzaga. */
	private record Metrics(Number filled, Number remaining, Number filledPercentage, Number emptyPercentage) {

		static Metrics of(StorageUnit unit, List<StorageUnitBalance> balances) {
			BigDecimal filled = balances.stream().filter(b -> b.getUnit() == unit.getCapacityUnit())
				.map(StorageUnitBalance::getCurrentQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal capacity = unit.getCapacity();
			BigDecimal remaining = capacity.subtract(filled).max(BigDecimal.ZERO);
			BigDecimal percentage = capacity.signum() > 0
				? filled.multiply(HUNDRED).divide(capacity, Quantities.BALANCE_SCALE, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;
			BigDecimal empty = HUNDRED.subtract(percentage).max(BigDecimal.ZERO);
			return new Metrics(Quantities.number(Quantities.balance(filled)),
				Quantities.number(Quantities.balance(remaining)),
				Quantities.number(percentage), Quantities.number(empty));
		}

	}

	private Map<Long, List<StorageUnitResponse.AllowedProduct>> allowedProducts(List<Long> unitIds) {
		List<StorageUnitAllowedProduct> rows = allowedProducts.findByStorageUnitIdInOrderByIdAsc(unitIds);
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

	private Map<Long, List<StorageUnitResponse.AllowedType>> allowedTypes(List<Long> unitIds) {
		List<StorageUnitAllowedType> rows = allowedTypes.findByStorageUnitIdInOrderByIdAsc(unitIds);
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

	/** Tanktaki aktif materyaller, tanga gore gruplu. */
	private Map<Long, List<MaterialResponse>> currentMaterials(List<Long> unitIds) {
		List<LocatedMaterial> rows = jdbc.sql("""
			select id, current_location_id from materials
			where current_location_id in (:ids) and current_location_type = 'storage_unit' and status = 'active'
			order by id
			""").param("ids", unitIds).query(LocatedMaterial.class).list();
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
