package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Tanka girebilecek urun kategorileri (tam liste). */
record SyncAllowedProductsRequest(
	@NotEmpty(message = LaravelMessages.REQUIRED)
	List<Long> productionProductIds) {
}
