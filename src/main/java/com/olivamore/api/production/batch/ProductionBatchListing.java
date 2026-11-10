package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Component;

import com.olivamore.api.shared.web.Pages;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parti listesi ({@code received_from}/{@code received_to}: varis gunu, dahil) ve takvimi. En yeni once. */
@Component
class ProductionBatchListing {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");
	private static final String DATE_FORMAT = "Y-m-d";
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("uuuu-MM-dd")
		.withResolverStyle(ResolverStyle.STRICT);

	private final ProductionBatchRepository batches;
	private final ProductionBatchResponses responses;
	private final BatchQueries queries;

	ProductionBatchListing(ProductionBatchRepository batches, ProductionBatchResponses responses, BatchQueries queries) {
		this.batches = batches;
		this.responses = responses;
		this.queries = queries;
	}

	PagedModel<ProductionBatchResponse> list(String receivedFrom, String receivedTo, Pageable pageable) {
		ValidationErrors errors = new ValidationErrors();
		LocalDate from = day(errors, "received_from", receivedFrom);
		LocalDate to = day(errors, "received_to", receivedTo);
		errors.throwIfAny();
		Specification<ProductionBatch> specification = received(from, to);
		return Pages.of(pageable, requested -> {
			Page<ProductionBatch> rows = batches.findAll(specification, Pages.sorted(requested, NEWEST_FIRST));
			return new PageImpl<>(responses.list(rows.getContent()), rows.getPageable(), rows.getTotalElements());
		}, () -> responses.list(batches.findAll(specification, NEWEST_FIRST)));
	}

	/** Gun -> parti sayisi; bas tarih gecersizse bitis de gecersiz sayilir. */
	Map<String, Long> calendar(String from, String to) {
		ValidationErrors errors = new ValidationErrors();
		LocalDate start = requiredDay(errors, "from", from);
		LocalDate end = requiredDay(errors, "to", to);
		if (to != null && end != null && (start == null || end.isBefore(start))) {
			errors.add("to", MessageKeys.AFTER_OR_EQUAL, "other", "from");
		}
		errors.throwIfAny();
		return queries.calendar(start, end);
	}

	private static Specification<ProductionBatch> received(LocalDate from, LocalDate to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), from.atStartOfDay()));
			}
			if (to != null) {
				predicates.add(cb.lessThan(root.get("receivedAt"), to.plusDays(1).atStartOfDay()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static LocalDate requiredDay(ValidationErrors errors, String field, String value) {
		if (value == null) {
			errors.add(field, MessageKeys.REQUIRED);
			return null;
		}
		return day(errors, field, value);
	}

	private static LocalDate day(ValidationErrors errors, String field, String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value, DAY);
		}
		catch (DateTimeParseException e) {
			errors.add(field, MessageKeys.DATE_FORMAT, "format", DATE_FORMAT);
			return null;
		}
	}

}
