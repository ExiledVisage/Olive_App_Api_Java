package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Tanka girebilecek urun kategorileri (tam liste). */
record SyncAllowedProductsRequest(
	@NotEmpty(message = MessageKeys.REQUIRED)
	List<Long> productionProductIds) {
}
