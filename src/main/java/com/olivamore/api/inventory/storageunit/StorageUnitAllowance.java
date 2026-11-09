package com.olivamore.api.inventory.storageunit;

import org.springframework.stereotype.Component;

/** Tanka girebilecek urun kategorisi ve tip sorulari (stok hareketleri kullanir). Liste bossa kisit yok. */
@Component
public class StorageUnitAllowance {

	private final StorageUnitAllowedProductRepository products;
	private final StorageUnitAllowedTypeRepository types;

	StorageUnitAllowance(StorageUnitAllowedProductRepository products, StorageUnitAllowedTypeRepository types) {
		this.products = products;
		this.types = types;
	}

	public boolean allowsProduct(long unitId, Long productId) {
		if (products.countByStorageUnitId(unitId) == 0) {
			return true;
		}
		return productId != null && products.existsByStorageUnitIdAndProductionProductId(unitId, productId);
	}

	public boolean allowsProductType(long unitId, long typeId) {
		if (types.countByStorageUnitId(unitId) == 0) {
			return true;
		}
		return types.existsByStorageUnitIdAndProductionProductTypeId(unitId, typeId);
	}

}
