package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.item.PackagingItem;
import com.olivamore.api.packaging.item.PackagingItemRepository;
import com.olivamore.api.packaging.lot.LotEnums.AdjustReason;
import com.olivamore.api.packaging.lot.LotEnums.MovementType;
import com.olivamore.api.packaging.lot.LotEnums.Source;
import com.olivamore.api.packaging.supplier.ProductSupplier;
import com.olivamore.api.packaging.supplier.ProductSupplierRepository;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Lotlar: mal girisi, kunye, sayim duzeltmesi, silme ve hareket defteri. Tek gercek kaynak lotlarin kalani;
 * {@code packaging_items.current_stock} onbellek.
 */
@Service
public class PackagingLotService {

	private static final String DUPLICATE_LOT = "Bu ambalaj kaleminde aynı lot numarası zaten girilmiş.";

	private final PackagingLotRepository lots;
	private final PackagingLotMovementRepository movements;
	private final RunPackagingRepository packagings;
	private final PackagingItemRepository items;
	private final ProductSupplierRepository suppliers;

	PackagingLotService(PackagingLotRepository lots, PackagingLotMovementRepository movements,
		RunPackagingRepository packagings, PackagingItemRepository items, ProductSupplierRepository suppliers) {
		this.lots = lots;
		this.movements = movements;
		this.packagings = packagings;
		this.items = items;
		this.suppliers = suppliers;
	}

	@Transactional(readOnly = true)
	void ensureItemExists(long itemId) {
		item(itemId);
	}

	@Transactional
	long receive(long itemId, CreatePackagingLotRequest request, long userId) {
		item(itemId);
		if (lots.existsByItemIdAndLotCode(itemId, request.lotCode())) {
			throw ValidationFailedException.of("lot_code", DUPLICATE_LOT);
		}
		return receive(itemId, request.lotCode(), request.quantity(), request.receivedAt(),
			supplier(request.productSupplierId()), request.note(), userId);
	}

	/** Yeni lot, giris hareketi, kalemin eldeki adedi. */
	public long receive(long itemId, String lotCode, int quantity, LocalDateTime receivedAt, Long supplierId,
		String note, long userId) {
		return receive(itemId, lotCode, quantity, receivedAt, supplier(supplierId), note, userId);
	}

	private long receive(long itemId, String lotCode, int quantity, LocalDateTime receivedAt, ProductSupplier supplier,
		String note, long userId) {
		LocalDateTime at = receivedAt == null ? Timestamps.now() : receivedAt;
		PackagingLot lot = lots.save(new PackagingLot(item(itemId), supplier, lotCode, quantity, at, note));
		movements.save(new PackagingLotMovement(lot, MovementType.IN, Source.SUPPLIER_DELIVERY, quantity, note, at,
			userId, null));
		recalculateItemStock(itemId);
		return lot.getId();
	}

	/** Kunye duzeltmesi: lot numarasi, tedarikci, giris zamani, not. */
	@Transactional
	void update(long lotId, UpdatePackagingLotRequest request) {
		PackagingLot lot = find(lotId);
		if (request.lotCode() != null && lots.existsByItemIdAndLotCodeAndIdNot(lot.getItem().getId(),
			request.lotCode(), lotId)) {
			throw ValidationFailedException.of("lot_code", DUPLICATE_LOT);
		}
		Patch.ifPresent(request.productSupplierId(), value -> lot.assignSupplier(supplier(value)));
		lot.apply(request);
	}

	/** Lot kilitlenir; kalanin altina inilemez; fire ve iade yalnizca dusus. */
	@Transactional
	void adjust(long lotId, AdjustPackagingLotRequest request, long userId) {
		int delta = request.delta();
		if (delta == 0) {
			throw ValidationFailedException.of("delta", "Düzeltme miktarı sıfır olamaz.");
		}
		PackagingLot lot = lots.findForUpdate(lotId)
			.orElseThrow(() -> new ModelNotFoundException("PackagingLot", lotId));
		int remaining = lot.getQuantityRemaining();
		if (delta < 0 && remaining + delta < 0) {
			throw new BusinessRuleException("'" + lot.getLotCode() + "' lotunda " + remaining + " adet kaldı; "
				+ Math.abs(delta) + " adet düşülemez.");
		}
		AdjustReason reason = request.reason() == null ? AdjustReason.COUNT : request.reason();
		if (reason != AdjustReason.COUNT && delta > 0) {
			throw new BusinessRuleException("Fire ve iade stoğu artırmaz; sayım fazlası için sebebi \"sayım farkı\" seçin.");
		}
		lot.changeRemaining(remaining + delta);
		movements.save(new PackagingLotMovement(lot, movementType(reason, delta), Source.MANUAL_ADJUSTMENT,
			Math.abs(delta), request.note(), Timestamps.now(), userId, null));
		recalculateItemStock(lot.getItem().getId());
	}

	/** Yalnizca hic kullanilmamis lot; hareketleriyle silinir. */
	@Transactional
	void delete(long lotId) {
		PackagingLot lot = find(lotId);
		String code = lot.getLotCode();
		if (!lot.untouched()) {
			throw new BusinessRuleException("'" + code + "' lotundan çıkış yapılmış, silinemez. "
				+ "Kalan miktarı sayım düzeltmesiyle sıfırlayabilirsiniz.");
		}
		if (packagings.existsByPackagingLotId(lotId)) {
			throw new BusinessRuleException("'" + code + "' lotu bir dolum koşusuna tahsis edilmiş, silinemez.");
		}
		long itemId = lot.getItem().getId();
		movements.deleteForLot(lotId);
		lots.delete(lot);
		lots.flush();
		recalculateItemStock(itemId);
	}

	@Transactional(readOnly = true)
	public boolean hasLots(long itemId) {
		return lots.existsByItemId(itemId);
	}

	/** Kalemin eldeki adedi = lotlarin kalani. */
	void recalculateItemStock(long itemId) {
		item(itemId).refreshStock(lots.sumRemaining(itemId));
	}

	private PackagingLot find(long id) {
		return lots.findById(id).orElseThrow(() -> new ModelNotFoundException("PackagingLot", id));
	}

	private PackagingItem item(long id) {
		return items.findById(id).orElseThrow(() -> new ModelNotFoundException("PackagingItem", id));
	}

	private ProductSupplier supplier(Long id) {
		if (id == null) {
			return null;
		}
		return suppliers.findById(id).orElseThrow(() -> ValidationFailedException.of("product_supplier_id",
			MessageKeys.EXISTS));
	}

	private static MovementType movementType(AdjustReason reason, int delta) {
		return switch (reason) {
			case SCRAP -> MovementType.SCRAP;
			case RETURN -> MovementType.RETURN;
			case COUNT -> delta > 0 ? MovementType.ADJUSTMENT_IN : MovementType.ADJUSTMENT_OUT;
		};
	}

}
