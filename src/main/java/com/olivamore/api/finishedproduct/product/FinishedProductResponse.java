package com.olivamore.api.finishedproduct.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.finishedproduct.RecipeResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Urunun API cevabi: rafta, sevkiyat bekleyen ve acikta adet dahil. {@code locations} ekleme ve guncelleme
 * cevabinda yok; {@code awaiting_shipment_stock} orada {@code null}.
 */
record FinishedProductResponse(
	Long id,
	String name,
	BigDecimal volumeLitre,
	int totalStock,
	long storedStock,
	Long awaitingShipmentStock,
	long unstoredStock,
	boolean isActive,
	RecipeResponse.Summary recipe,
	List<Component> components,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Location> locations,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Component(Long id, Long packagingItemId, String packagingItemName, String packagingItemType,
		int quantity) {
	}

	record Location(Long storageId, String storageName, Long slotId, String slotName, long quantity) {
	}

}
