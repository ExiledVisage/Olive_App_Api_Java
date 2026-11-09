package com.olivamore.api.finishedproduct.storage;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** Deponun API cevabi; ekleme cevabinda {@code slots} ve {@code stock} yok. */
record StorageResponse(
	Long id,
	String name,
	String description,
	boolean isActive,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<SlotResponse> slots,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Stock> stock,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Stock(Long id, StockProduct finishedProduct, int quantity, LocalDateTime updatedAt) {
	}

	record StockProduct(Long id, String name, Double volumeLitre) {
	}

}
