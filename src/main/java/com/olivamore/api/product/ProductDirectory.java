package com.olivamore.api.product;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Urun kategorileri, tipleri ve attribute tanimlari: diger modullerin okudugu tek kapi. */
@Component
public class ProductDirectory {

	public record Product(Long id, String key, String name, String description, BigDecimal defaultDensityKgPerLitre) {
	}

	public record ProductType(Long id, Long productionProductId, String name, String code, String type, String unit,
		BigDecimal densityKgPerLitre, BigDecimal defaultDensityKgPerLitre) {
	}

	/** Urun-attribute eslemesi ve global attribute tanimi. */
	public record AttributeMapping(Long id, Long productionProductId, Long attributeDefinitionId, Boolean isRequired,
		Integer sortOrder, String attributeKey, String label, String dataType, String unit, String description) {
	}

	private record LabelRow(Long id, String label) {
	}

	private static final String TYPES = """
		select t.id, t.production_product_id, t.name, t.code, t.type, t.unit, t.density_kg_per_litre,
			p.default_density_kg_per_litre
		from production_product_types t left join production_products p on p.id = t.production_product_id
		""";

	private static final String MAPPINGS = """
		select m.id, m.production_product_id, m.attribute_definition_id, m.is_required, m.sort_order,
			a.attribute_key, a.label, a.data_type, a.unit, a.description
		from production_product_attribute_definitions m
		left join attribute_definitions a on a.id = m.attribute_definition_id
		""";

	private final JdbcClient jdbc;

	ProductDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ---- kategoriler ------------------------------------------------------

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, Product> products(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select id, key, name, description, default_density_kg_per_litre from production_products where id in (:ids)
			""").param("ids", present).query(Product.class).list().stream()
			.collect(Collectors.toMap(Product::id, Function.identity()));
	}

	public Optional<String> productName(long id) {
		return jdbc.sql("select name from production_products where id = :id").param("id", id)
			.query(String.class).optional();
	}

	public Set<Long> existingProducts(Collection<Long> ids) {
		return existing("production_products", ids);
	}

	// ---- tipler -----------------------------------------------------------

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, ProductType> productTypes(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql(TYPES + " where t.id in (:ids)").param("ids", present).query(ProductType.class).list().stream()
			.collect(Collectors.toMap(ProductType::id, Function.identity()));
	}

	public Optional<ProductType> productType(long id) {
		return Optional.ofNullable(productTypes(List.of(id)).get(id));
	}

	public Optional<ProductType> productTypeOf(long productId, String type) {
		return jdbc.sql(TYPES + " where t.production_product_id = :product and t.type = :type limit 1")
			.param("product", productId).param("type", type).query(ProductType.class).optional();
	}

	public Optional<ProductType> untypedProductTypeOf(long productId) {
		return jdbc.sql(TYPES + " where t.production_product_id = :product and t.type is null limit 1")
			.param("product", productId).query(ProductType.class).optional();
	}

	public Set<Long> existingTypes(Collection<Long> ids) {
		return existing("production_product_types", ids);
	}

	/** Kategorilerin tipleri. */
	public List<Long> typeIdsOfProducts(Collection<Long> productIds) {
		List<Long> present = ValidationErrors.present(productIds);
		if (present.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select id from production_product_types where production_product_id in (:ids)")
			.param("ids", present).query(Long.class).list();
	}

	/** Adinda aranan metin gecen tipler (buyuk-kucuk harf duyarsiz). */
	public List<Long> typeIdsNamed(String like) {
		return jdbc.sql("select id from production_product_types where name::text ilike :like").param("like", like)
			.query(Long.class).list();
	}

	// ---- attribute'lar ----------------------------------------------------

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, AttributeMapping> attributeMappings(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql(MAPPINGS + " where m.id in (:ids)").param("ids", present).query(AttributeMapping.class).list()
			.stream().collect(Collectors.toMap(AttributeMapping::id, Function.identity()));
	}

	public Set<Long> existingAttributeMappings(Collection<Long> ids) {
		return existing("production_product_attribute_definitions", ids);
	}

	public Set<Long> attributeMappingIdsOfProduct(Long productId) {
		if (productId == null) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql(
				"select id from production_product_attribute_definitions where production_product_id = :p")
			.param("p", productId).query(Long.class).list());
	}

	/** Kategoride bu id'li esleme. */
	public Optional<AttributeMapping> attributeMappingOf(long productId, long id) {
		return jdbc.sql(MAPPINGS + " where m.production_product_id = :product and m.id = :id limit 1")
			.param("product", productId).param("id", id).query(AttributeMapping.class).optional();
	}

	/** Kategoride bu anahtarli esleme. */
	public Optional<AttributeMapping> attributeMappingByKey(long productId, String attributeKey) {
		return jdbc.sql(MAPPINGS + " where m.production_product_id = :product and a.attribute_key = :key limit 1")
			.param("product", productId).param("key", attributeKey).query(AttributeMapping.class).optional();
	}

	public Optional<String> attributeDataType(Long attributeDefinitionId) {
		if (attributeDefinitionId == null) {
			return Optional.empty();
		}
		return jdbc.sql("select data_type from attribute_definitions where id = :id").param("id", attributeDefinitionId)
			.query(String.class).optional();
	}

	/** Global attribute tanimlarinin etiketleri. */
	public Map<Long, String> attributeLabels(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, label from attribute_definitions where id in (:ids)").param("ids", present)
			.query(LabelRow.class).list().stream().collect(Collectors.toMap(LabelRow::id, LabelRow::label));
	}

	public Set<Long> existingAttributeDefinitions(Collection<Long> ids) {
		return existing("attribute_definitions", ids);
	}

	private Set<Long> existing(String table, Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("select id from " + table + " where id in (:ids)").param("ids", present)
			.query(Long.class).list());
	}

}
