package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.finishedproduct.storage.SlotResponse.IdName;

import java.time.LocalDateTime;
import java.util.UUID;

record MovementResponse(
	Long id,
	MovementType movementType,
	int quantity,
	int signedQuantity,
	UUID transferReference,
	String note,
	LocalDateTime movedAt,
	IdName finishedProduct,
	IdName slot,
	CounterpartySlot counterpartySlot,
	IdName createdByUser) {

	record CounterpartySlot(Long id, String name, String storageName) {
	}

}
