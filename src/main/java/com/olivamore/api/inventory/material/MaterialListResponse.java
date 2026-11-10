package com.olivamore.api.inventory.material;

import com.olivamore.api.inventory.MaterialResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Materyal listesi: en yeni {@code meta.limit} kayit. */
record MaterialListResponse(List<Item> data, Meta meta) {

	record Meta(int limit) {
	}

	record Item(
		Long id,
		BigDecimal quantity,
		String unit,
		String status,
		String lineageRole,
		Long parentMaterialId,
		String originType,
		MaterialDetailResponse.TypeSummary productionProductType,
		MaterialResponse.UnitSummary storageUnit,
		String batchCode,
		LocalDateTime createdAt) {
	}

}
