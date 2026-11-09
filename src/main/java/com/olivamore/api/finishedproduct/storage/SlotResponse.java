package com.olivamore.api.finishedproduct.storage;

import java.time.LocalDateTime;
import java.util.List;

/** Rafin API cevabi; hacim ekranlarda sayi olarak. */
record SlotResponse(
	Long id,
	String name,
	Double volumeLitre,
	int maxQuantity,
	int currentQuantity,
	int available,
	IdName finishedProduct,
	List<Content> contents,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Content(IdName finishedProduct, int quantity) {
	}

	record IdName(Long id, String name) {
	}

}
