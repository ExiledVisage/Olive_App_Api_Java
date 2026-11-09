package com.olivamore.api.packaging.lot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.packaging.item.PackagingItem;
import com.olivamore.api.packaging.item.PackagingItemType;
import com.olivamore.api.packaging.supplier.ProductSupplier;

import java.time.LocalDateTime;
import java.util.List;

/** Lotun API cevabi. {@code movements} ve {@code runs} yalnizca detayda. */
public record PackagingLotResponse(
	Long id,
	String lotCode,
	LocalDateTime receivedAt,
	int quantityReceived,
	int quantityRemaining,
	int quantityUsed,
	String status,
	String note,
	ItemSummary packagingItem,
	SupplierSummary productSupplier,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Movement> movements,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<Run> runs,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public record ItemSummary(Long id, String name, PackagingItemType type) {
	}

	public record SupplierSummary(Long id, String name, String code) {
	}

	public record Movement(
		Long id,
		String movementType,
		String sourceType,
		int quantity,
		boolean isIncoming,
		String note,
		LocalDateTime movedAt,
		Long finishedProductRunId,
		String finishedProductName,
		LocalDateTime createdAt) {
	}

	public record Run(
		Long id,
		String status,
		int quantity,
		String finishedProductName,
		Integer bottleQuantity,
		LocalDateTime completedAt) {
	}

	static PackagingLotResponse of(PackagingLot lot, List<Movement> movements, List<Run> runs) {
		PackagingItem item = lot.getItem();
		ProductSupplier supplier = lot.getSupplier();
		return new PackagingLotResponse(lot.getId(), lot.getLotCode(), lot.getReceivedAt(), lot.getQuantityReceived(),
			lot.getQuantityRemaining(), lot.getQuantityReceived() - lot.getQuantityRemaining(),
			lot.getStatus().value(), lot.getNote(),
			new ItemSummary(item.getId(), item.getName(), item.getType()),
			supplier == null ? null : new SupplierSummary(supplier.getId(), supplier.getName(), supplier.getCode()),
			movements, runs, lot.getCreatedAt(), lot.getUpdatedAt());
	}

}
