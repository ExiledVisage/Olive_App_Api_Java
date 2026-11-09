package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tankin hareketleri ve tarih aralikli gecmisi: en yeni once; {@code per_page} varsa sayfali. */
@Component
public class StorageMovementListing {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("movedAt"), Sort.Order.desc("id"));

	private final StorageMovementRepository movements;
	private final StorageMovementResponses responses;

	StorageMovementListing(StorageMovementRepository movements, StorageMovementResponses responses) {
		this.movements = movements;
		this.responses = responses;
	}

	/** Tankin butun hareketleri. */
	public ListResponse<StorageMovementResponse> forUnit(long unitId, HttpServletRequest request) {
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(responses.list(movements.findByStorageUnitIdOrderByMovedAtDescIdDesc(unitId)));
		}
		int page = Listing.currentPage(request);
		Page<StorageMovement> rows = movements.findByStorageUnitIdOrderByMovedAtDescIdDesc(unitId,
			PageRequest.of(page - 1, perPage.get()));
		return LaravelPaginator.paginate(responses.list(rows.getContent()), rows.getTotalElements(), page, perPage.get(),
			request);
	}

	/** {@code date_from}, {@code date_to}: moved_at tarih araligi ("0" verilmemis sayilir). */
	ListResponse<StorageMovementResponse> history(long unitId, String dateFrom, String dateTo,
		HttpServletRequest request) {
		LocalDate from = date("date_from", dateFrom);
		LocalDate to = date("date_to", dateTo);
		Specification<StorageMovement> specification = forUnit(unitId, from, to);
		Optional<Integer> perPage = Listing.perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(responses.list(movements.findAll(specification, NEWEST_FIRST)));
		}
		int page = Listing.currentPage(request);
		Page<StorageMovement> rows = movements.findAll(specification,
			PageRequest.of(page - 1, perPage.get(), NEWEST_FIRST));
		return LaravelPaginator.paginate(responses.list(rows.getContent()), rows.getTotalElements(), page, perPage.get(),
			request);
	}

	private static Specification<StorageMovement> forUnit(long unitId, LocalDate from, LocalDate to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("storageUnitId"), unitId));
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
			throw new LaravelValidationException(
				java.util.Map.of(field, List.of(LaravelMessages.format(LaravelMessages.DATE, field))));
		}
	}

}
