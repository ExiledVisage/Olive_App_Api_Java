package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.lot.LotEnums.MovementType;
import com.olivamore.api.packaging.lot.LotEnums.ScrapReason;
import com.olivamore.api.packaging.lot.LotEnums.Source;
import com.olivamore.api.packaging.lot.LotEnums.Status;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Dolum kosusunda kirik / hatali ambalaj (fire): lotun kalanindan duser, kosuya bagli SCRAP hareketi yazilir.
 * Lot verilmezse kosunun o kalem icin tahsis ettigi lotlardan yeterli olan ilki, o da yoksa kalemin en eski
 * girisli aktif lotu. Fire kosunun uretim adedini degistirmez. Geri alinca adet lota doner, hareket silinir.
 */
@Service
public class RunScrapService {

	private final PackagingLotRepository lots;
	private final RunPackagingRepository packagings;
	private final PackagingLotMovementRepository movements;
	private final PackagingLotService lotService;

	RunScrapService(PackagingLotRepository lots, RunPackagingRepository packagings,
		PackagingLotMovementRepository movements, PackagingLotService lotService) {
		this.lots = lots;
		this.packagings = packagings;
		this.movements = movements;
		this.lotService = lotService;
	}

	/** @return yazilan fire hareketinin id'si */
	@Transactional
	public long scrap(long runId, long itemId, Long lotId, int quantity, String reason, String note, long userId) {
		ScrapReason scrapReason = reason(reason);
		if (quantity <= 0) {
			throw ValidationFailedException.of("quantity", MessageKeys.MIN);
		}
		PackagingLot lot = lots.findForUpdate(lotId != null ? lotId : chooseLot(runId, itemId, quantity))
			.orElseThrow(() -> new ModelNotFoundException("PackagingLot", lotId));
		if (!Objects.equals(lot.getItem().getId(), itemId)) {
			throw ValidationFailedException.of("packaging_lot_id", "Lot bu ambalaj kalemine ait değil.");
		}
		if (lot.getQuantityRemaining() < quantity) {
			throw new BusinessRuleException("'" + lot.getLotCode() + "' lotunda " + lot.getQuantityRemaining()
				+ " adet kaldı; " + quantity + " adet fire düşülemez.");
		}
		lot.changeRemaining(lot.getQuantityRemaining() - quantity);
		PackagingLotMovement movement = movements.save(PackagingLotMovement.runScrap(lot, quantity, scrapReason, note,
			Timestamps.now(), userId, runId));
		lotService.recalculateItemStock(itemId);
		return movement.getId();
	}

	/** Kosunun fire hareketi geri alinir: adet lota doner. */
	@Transactional
	public void undo(long runId, long movementId) {
		PackagingLotMovement movement = movements.findById(movementId)
			.filter(m -> m.getMovementType() == MovementType.SCRAP && m.getSourceType() == Source.FINISHED_PRODUCT_RUN
				&& Objects.equals(m.getFinishedProductRunId(), runId))
			.orElseThrow(() -> new ModelNotFoundException("PackagingScrap", movementId));
		PackagingLot lot = lots.findForUpdate(movement.getPackagingLotId()).orElseThrow();
		lot.changeRemaining(lot.getQuantityRemaining() + movement.getQuantity());
		movements.delete(movement);
		lotService.recalculateItemStock(movement.getPackagingItemId());
	}

	private long chooseLot(long runId, long itemId, int quantity) {
		List<Long> allocated = packagings.findByFinishedProductRunIdOrderByIdAsc(runId).stream()
			.filter(p -> Objects.equals(p.getPackagingItemId(), itemId)).map(RunPackaging::getPackagingLotId).toList();
		for (PackagingLot lot : lots.findByIdIn(allocated)) {
			if (lot.getQuantityRemaining() >= quantity) {
				return lot.getId();
			}
		}
		return lots.findByItemIdAndStatusAndQuantityRemainingGreaterThanOrderByReceivedAtAscIdAsc(itemId,
				Status.ACTIVE, quantity - 1).stream().findFirst()
			.orElseThrow(() -> new BusinessRuleException("Bu ambalaj için " + quantity
				+ " adet fire düşülecek lot yok.")).getId();
	}

	private static ScrapReason reason(String value) {
		return Arrays.stream(ScrapReason.values()).filter(r -> r.value().equals(value)).findFirst()
			.orElseThrow(() -> ValidationFailedException.of("reason", MessageKeys.IN));
	}

}
