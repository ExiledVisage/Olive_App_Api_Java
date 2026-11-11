package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tanklar, izinli urun ve tipler, attribute kurallari (yalnizca decimal attribute, min <= max; sonraki kuralda
 * hata olursa hepsi geri alinir), silme korumalari. Tank turu yalnizca tank bosken degisir.
 */
@Service
class StorageUnitService {

	private final StorageUnitRepository units;
	private final StorageUnitAllowedProductRepository allowedProducts;
	private final StorageUnitAllowedTypeRepository allowedTypes;
	private final StorageUnitAttributeRuleRepository rules;
	private final JdbcClient jdbc;
	private final ProductDirectory directory;

	StorageUnitService(StorageUnitRepository units, StorageUnitAllowedProductRepository allowedProducts,
		StorageUnitAllowedTypeRepository allowedTypes, StorageUnitAttributeRuleRepository rules, JdbcClient jdbc,
		ProductDirectory directory) {
		this.units = units;
		this.allowedProducts = allowedProducts;
		this.allowedTypes = allowedTypes;
		this.rules = rules;
		this.jdbc = jdbc;
		this.directory = directory;
	}

	StorageUnit find(long id) {
		return units.findById(id).orElseThrow(() -> new ModelNotFoundException("StorageUnit", id));
	}

	@Transactional
	long create(CreateStorageUnitRequest request) {
		if (units.existsByCode(request.code())) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("code", MessageKeys.UNIQUE);
			errors.throwIfAny();
		}
		return units.save(new StorageUnit(request)).getId();
	}

	@Transactional
	void update(long id, UpdateStorageUnitRequest request) {
		StorageUnit unit = find(id);
		if (request.code() != null && units.existsByCodeAndIdNot(request.code(), id)) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("code", MessageKeys.UNIQUE);
			errors.throwIfAny();
		}
		if (request.kind() != null && request.kind() != unit.getKind()) {
			long materialCount = activeMaterialCount(id);
			if (materialCount > 0) {
				throw new BusinessRuleException("'" + unit.getCode() + "' tankında " + materialCount
					+ " aktif materyal var; tank türü yalnızca tank boşken değiştirilebilir.");
			}
		}
		unit.apply(request);
	}

	/** Icinde aktif materyal ya da hareket kaydi olan tank silinmez (pasife alinir). */
	@Transactional
	void delete(long id) {
		StorageUnit unit = find(id);
		long movementCount = count("select count(*) from storage_movements where storage_unit_id = :unit", id);
		long materialCount = activeMaterialCount(id);
		if (materialCount > 0) {
			throw new BusinessRuleException("'" + unit.getCode() + "' tankında " + materialCount + " aktif materyal var; "
				+ "silinemez. Önce tankı boşaltın.");
		}
		if (movementCount > 0) {
			throw new BusinessRuleException("'" + unit.getCode() + "' tankının " + movementCount + " hareket kaydı var; "
				+ "silinemez. Tankı kullanımdan kaldırmak için is_active alanını false yapın.");
		}
		units.delete(unit);
	}

	/** Liste tam hali: listede olmayanlar silinir, eksikler (istek sirasiyla) eklenir. */
	@Transactional
	void syncAllowedProducts(long id, SyncAllowedProductsRequest request) {
		find(id);
		Set<Long> ids = distinct(request.productionProductIds());
		ValidationErrors errors = new ValidationErrors();
		errors.exists("production_product_ids", null, request.productionProductIds(),
			directory.existingProducts(ids));
		errors.throwIfAny();
		allowedProducts.deleteOthers(id, ids);
		Set<Long> existing = allowedProducts.findByStorageUnitId(id).stream()
			.map(StorageUnitAllowedProduct::getProductionProductId).collect(Collectors.toSet());
		ids.stream().filter(pid -> !existing.contains(pid))
			.forEach(pid -> allowedProducts.save(new StorageUnitAllowedProduct(id, pid)));
	}

	@Transactional
	void syncAllowedTypes(long id, SyncAllowedTypesRequest request) {
		find(id);
		Set<Long> ids = distinct(request.productionProductTypeIds());
		ValidationErrors errors = new ValidationErrors();
		errors.exists("production_product_type_ids", null, request.productionProductTypeIds(),
			directory.existingTypes(ids));
		errors.throwIfAny();
		allowedTypes.deleteOthers(id, ids);
		Set<Long> existing = allowedTypes.findByStorageUnitId(id).stream()
			.map(StorageUnitAllowedType::getProductionProductTypeId).collect(Collectors.toSet());
		ids.stream().filter(tid -> !existing.contains(tid))
			.forEach(tid -> allowedTypes.save(new StorageUnitAllowedType(id, tid)));
	}

	/** Listede olmayan kurallar silinir, digerleri eklenir ya da guncellenir. */
	@Transactional
	void syncAttributeRules(long id, SyncAttributeRulesRequest request) {
		find(id);
		List<Long> definitionIds = request.rules().stream()
			.map(SyncAttributeRulesRequest.Rule::productionProductAttributeDefinitionId).toList();
		Set<Long> ids = distinct(definitionIds);
		ValidationErrors errors = new ValidationErrors();
		errors.exists("rules", "production_product_attribute_definition_id", definitionIds,
			directory.existingAttributeMappings(ids));
		errors.throwIfAny();

		Map<Long, ProductDirectory.AttributeMapping> definitions = directory.attributeMappings(ids);
		rules.deleteOthers(id, ids);
		for (SyncAttributeRulesRequest.Rule rule : request.rules()) {
			ProductDirectory.AttributeMapping definition = definitions.get(rule.productionProductAttributeDefinitionId());
			if (definition != null && !"decimal".equals(definition.dataType())) {
				throw new BusinessRuleException("Tank attribute rule sadece decimal attribute'lar için tanımlanabilir. "
					+ "'" + Objects.requireNonNullElse(definition.label(), "") + "' attribute'unun tipi: "
					+ Objects.requireNonNullElse(definition.dataType(), "") + ".");
			}
			if (rule.minValueDecimal() != null && rule.maxValueDecimal() != null
				&& rule.minValueDecimal().compareTo(rule.maxValueDecimal()) > 0) {
				throw new BusinessRuleException("Attribute rule içinde min değer max değerden büyük olamaz.");
			}
			StorageUnitAttributeRule entity = rules
				.findByStorageUnitIdAndDefinitionId(id, rule.productionProductAttributeDefinitionId())
				.orElseGet(() -> new StorageUnitAttributeRule(id, rule.productionProductAttributeDefinitionId()));
			entity.apply(rule.minValueDecimal(), rule.maxValueDecimal(),
				rule.applyMode() == null ? ApplyMode.INCOMING_AND_AVERAGE : rule.applyMode(),
				rule.isActive() == null || rule.isActive(), rule.note());
			rules.save(entity);
		}
	}

	// ---- yardimcilar -------------------------------------------------------

	private static Set<Long> distinct(List<Long> values) {
		return new LinkedHashSet<>(ValidationErrors.present(values));
	}

	private long activeMaterialCount(long unitId) {
		return count("""
			select count(*) from materials
			where current_location_type = 'storage_unit' and current_location_id = :unit and status = 'active'
			""", unitId);
	}

	private long count(String sql, long unitId) {
		return jdbc.sql(sql).param("unit", unitId).query(Long.class).single();
	}

}
