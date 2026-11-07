package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/PackagingLot}: StorePackagingLotRequest, UpdatePackagingLotRequest, AdjustPackagingLotRequest. */
final class PackagingLotRequests {

	static final String DUPLICATE_LOT = "Bu ambalaj kaleminde aynı lot numarası zaten girilmiş.";

	private PackagingLotRequests() {
	}

	/** Mal girisi; lot numarasi kalem icinde tekil. */
	static Rules store(long itemId) {
		return Rules.of()
			.field("lot_code", "required|string|max:100|unique:packaging_lots,lot_code,NULL,id,packaging_item_id," + itemId)
			.field("quantity", "required|integer|min:1")
			.field("received_at", "nullable|date")
			.field("product_supplier_id", "nullable|integer|exists:product_suppliers,id")
			.field("note", "nullable|string")
			.message("lot_code.unique", DUPLICATE_LOT);
	}

	/** Kunye duzeltmesi (miktar yok). */
	static Rules update(Row lot) {
		return Rules.of()
			.field("lot_code", "sometimes|string|max:100|unique:packaging_lots,lot_code," + lot.id() + ",id,packaging_item_id,"
				+ lot.longValue("packaging_item_id"))
			.field("received_at", "sometimes|date")
			.field("product_supplier_id", "nullable|integer|exists:product_suppliers,id")
			.field("note", "nullable|string")
			.message("lot_code.unique", DUPLICATE_LOT);
	}

	/** Sayim duzeltmesi, fire, iade; {@code delta} isaretli, sifir olamaz. */
	static Rules adjust() {
		return Rules.of()
			.field("delta", "required|integer|not_in:0")
			.field("reason", "sometimes|in:COUNT,SCRAP,RETURN")
			.field("note", "nullable|string")
			.message("delta.not_in", "Düzeltme miktarı sıfır olamaz.");
	}

}
