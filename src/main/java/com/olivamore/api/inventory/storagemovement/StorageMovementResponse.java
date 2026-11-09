package com.olivamore.api.inventory.storagemovement;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/** Hareket cevabi; {@code storage_unit} yalnizca yazma cevaplarinda var. */
public record StorageMovementResponse(
	Long id,
	Long storageUnitId,
	Long materialId,
	Long batchProcessId,
	Long finishedProductRunId,
	Long counterpartyStorageUnitId,
	MovementSource sourceType,
	MovementType movementType,
	String transferReference,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	BigDecimal quantity,
	MaterialUnit unit,
	String note,
	LocalDateTime movedAt,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	Optional<MaterialResponse.UnitSummary> storageUnit,
	MaterialResponse material,
	MaterialResponse.Process batchProcess,
	MaterialResponse.UnitSummary counterpartyStorageUnit,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	/** Transfer cevabi (200). */
	public record Transfer(String transferReference, StorageMovementResponse outMovement,
		StorageMovementResponse inMovement) {
	}

}
