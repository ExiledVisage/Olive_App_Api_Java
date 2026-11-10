package com.olivamore.api.product;

import com.olivamore.api.productionline.ProductCatalog;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Uretim hatti modulunun istedigi urun bilgileri. */
@Component
class ProductionLineProductCatalog implements ProductCatalog {

	private final ProductDirectory directory;

	ProductionLineProductCatalog(ProductDirectory directory) {
		this.directory = directory;
	}

	@Override
	public Map<Long, Product> products(Collection<Long> ids) {
		return directory.products(ids).values().stream().collect(Collectors.toMap(ProductDirectory.Product::id,
			p -> new Product(p.id(), p.key(), p.name(), p.description())));
	}

	@Override
	public Map<Long, ProductType> productTypes(Collection<Long> ids) {
		return directory.productTypes(ids).values().stream().collect(Collectors.toMap(ProductDirectory.ProductType::id,
			t -> new ProductType(t.id(), t.name(), t.code(), t.type(), t.unit())));
	}

}
