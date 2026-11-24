package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.web.Pages;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Tankin hareketleri ve tarih aralikli gecmisi: en yeni once; {@code per_page} varsa sayfali. */
@Component
public class StorageMovementListing {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("movedAt"), Sort.Order.desc("id"));

	/** Defter: kayit sirasinin tersi (bakiye bu sirayla hesaplanir; moved_at kayit ani). */
	private static final Sort LEDGER_ORDER = Sort.by(Sort.Order.desc("id"));

	private final StorageMovementRepository movements;
	private final StorageMovementResponses responses;
	private final TankLedger ledger;

	StorageMovementListing(StorageMovementRepository movements, StorageMovementResponses responses,
		TankLedger ledger) {
		this.movements = movements;
		this.responses = responses;
		this.ledger = ledger;
	}

	/** Tankin butun hareketleri. */
	public PagedModel<StorageMovementResponse> forUnit(long unitId, Pageable pageable) {
		return Pages.of(pageable,
			requested -> page(movements.findByStorageUnitId(unitId, Pages.sorted(requested, NEWEST_FIRST))),
			() -> responses.list(movements.findByStorageUnitId(unitId, NEWEST_FIRST)));
	}

	/**
	 * Tankin islem gecmisi (defter): {@code date_from}, {@code date_to} moved_at tarih araligi ("0" verilmemis
	 * sayilir), {@code materialId} verilirse yalnizca o materyal. Satirlarda bakiye ve defter alanlari
	 * ({@link TankLedger}).
	 */
	PagedModel<StorageMovementResponse.HistoryEntry> history(long unitId, Long materialId, String dateFrom,
		String dateTo, Pageable pageable) {
		LocalDate from = date("date_from", dateFrom);
		LocalDate to = date("date_to", dateTo);
		Specification<StorageMovement> specification = forUnit(unitId, materialId, from, to);
		return Pages.of(pageable, requested -> {
			Page<StorageMovement> rows = movements.findAll(specification, Pages.sorted(requested, LEDGER_ORDER));
			return new PageImpl<>(entries(unitId, rows.getContent()), rows.getPageable(), rows.getTotalElements());
		}, () -> entries(unitId, movements.findAll(specification, LEDGER_ORDER)));
	}

	/** Defter ozeti: {@code materialId} verilirse o materyal, yoksa tankta su an aktif materyaller. */
	List<StorageMovementResponse.LedgerSummary> summary(long unitId, Long materialId) {
		return ledger.summaries(unitId, materialId == null ? List.of() : List.of(materialId));
	}

	private List<StorageMovementResponse.HistoryEntry> entries(long unitId, List<StorageMovement> rows) {
		return ledger.entries(unitId, rows, responses.list(rows));
	}

	private Page<StorageMovementResponse> page(Page<StorageMovement> rows) {
		List<StorageMovementResponse> content = responses.list(rows.getContent());
		return new PageImpl<>(content, rows.getPageable(), rows.getTotalElements());
	}

	private static Specification<StorageMovement> forUnit(long unitId, Long materialId, LocalDate from,
		LocalDate to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("storageUnitId"), unitId));
			if (materialId != null) {
				predicates.add(cb.equal(root.get("materialId"), materialId));
			}
			Expression<LocalDate> movedDate = cb.function("date", LocalDate.class, root.get("movedAt"));
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(movedDate, from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(movedDate, to));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static LocalDate date(String field, String value) {
		if (value == null || value.equals("0")) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException e) {
			throw ValidationFailedException.of(field, MessageKeys.DATE);
		}
	}

}
