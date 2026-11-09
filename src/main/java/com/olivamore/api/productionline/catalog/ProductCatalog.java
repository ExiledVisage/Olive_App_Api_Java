package com.olivamore.api.productionline.catalog;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hatlarin ve uzantilarin gosterdigi urun bilgileri, salt okunur. {@code product} modulu bu modulu
 * kullandigi icin ters yonde bagimlilik donguye girer; bu yuzden tablolardan okunur.
 */
@Component
public class ProductCatalog {

	public record Product(Long id, String key, String name, String description) {
	}

	public record ProductType(Long id, String name, String code, String type, String unit) {
	}

	private final JdbcClient jdbc;

	ProductCatalog(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Map<Long, Product> products(List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, key, name, description from production_products where id in (:ids)")
			.param("ids", present)
			.query(Product.class)
			.list().stream()
			.collect(Collectors.toMap(Product::id, Function.identity()));
	}

	public Map<Long, ProductType> productTypes(List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, code, type, unit from production_product_types where id in (:ids)")
			.param("ids", present)
			.query(ProductType.class)
			.list().stream()
			.collect(Collectors.toMap(ProductType::id, Function.identity()));
	}

}
