package com.olivamore.api.productionline;

import java.util.Collection;
import java.util.Map;

/**
 * Hatlarin ve uzantilarin gosterdigi urun bilgileri. {@code product} modulu urun ekranlarinda hatlari gosterdigi
 * icin bu module bagimlidir; bu arayuzu o uygular, bagimlilik donguye girmez.
 */
public interface ProductCatalog {

	record Product(Long id, String key, String name, String description) {
	}

	record ProductType(Long id, String name, String code, String type, String unit) {
	}

	/** Bulunamayan id'ler haritada yok. */
	Map<Long, Product> products(Collection<Long> ids);

	/** Bulunamayan id'ler haritada yok. */
	Map<Long, ProductType> productTypes(Collection<Long> ids);

}
