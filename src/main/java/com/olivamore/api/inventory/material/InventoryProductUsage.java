package com.olivamore.api.inventory.material;

import com.olivamore.api.product.ProductUsage;
import com.olivamore.api.product.ProductUsageCounter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/** Urun kategorisini, tipini ve attribute eslemesini kullanan tank ve materyal kayitlari. */
@Component
class InventoryProductUsage implements ProductUsageCounter {

	private final JdbcClient jdbc;

	InventoryProductUsage(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<ProductUsage, Long> ofProduct(long productId, Collection<Long> attributeMappingIds) {
		return Map.of(
			ProductUsage.TANK_BALANCE, count("storage_unit_balances", "production_product_id", productId),
			ProductUsage.TANK_PRODUCT_PERMISSION,
			count("storage_unit_allowed_production_products", "production_product_id", productId),
			ProductUsage.MATERIAL_ATTRIBUTE_VALUE, countMappings("material_attribute_values", attributeMappingIds),
			ProductUsage.TANK_ATTRIBUTE_RULE, countMappings("storage_unit_attribute_rules", attributeMappingIds));
	}

	@Override
	public Map<ProductUsage, Long> ofProductType(long typeId) {
		return Map.of(
			ProductUsage.MATERIAL, count("materials", "production_product_type_id", typeId),
			ProductUsage.TANK_TYPE_PERMISSION,
			count("storage_unit_allowed_production_product_types", "production_product_type_id", typeId));
	}

	@Override
	public Map<ProductUsage, Long> ofAttributeMapping(long mappingId) {
		return Map.of(
			ProductUsage.MATERIAL_ATTRIBUTE_VALUE,
			count("material_attribute_values", "production_product_attribute_definition_id", mappingId),
			ProductUsage.TANK_ATTRIBUTE_RULE,
			count("storage_unit_attribute_rules", "production_product_attribute_definition_id", mappingId));
	}

	private long count(String table, String column, long id) {
		return jdbc.sql("select count(*) from " + table + " where " + column + " = ?").param(id)
			.query(Long.class).single();
	}

	private long countMappings(String table, Collection<Long> mappingIds) {
		if (mappingIds.isEmpty()) {
			return 0;
		}
		return jdbc.sql("select count(*) from " + table + " where production_product_attribute_definition_id in (:ids)")
			.param("ids", mappingIds).query(Long.class).single();
	}

}
