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
import java.util.Set;

/**
 * Parti listesi ({@code received_from}/{@code received_to}: varis gunu, dahil; {@code kind}: delivery |
 * processable) ve takvimi. En yeni once.
 */
@Component
class ProductionBatchListing {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
	private static final String DATE_FORMAT = "Y-m-d";
	private static final String DELIVERY = "delivery";
	private static final String PROCESSABLE = "processable";
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

	PagedModel<ProductionBatchResponse> list(String receivedFrom, String receivedTo, String kind, Pageable pageable) {
		ValidationErrors errors = new ValidationErrors();
		LocalDate from = day(errors, "received_from", receivedFrom);
		LocalDate to = day(errors, "received_to", receivedTo);
		if (kind != null && !DELIVERY.equals(kind) && !PROCESSABLE.equals(kind)) {
			errors.add("kind", MessageKeys.IN);
		}
		errors.throwIfAny();
		Specification<ProductionBatch> specification = received(from, to).and(kind(kind));
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

	/** Teslimat: grup degil. Islenen: grup ya da makineli asamasi olmayan hattin partisi. */
	private Specification<ProductionBatch> kind(String kind) {
		if (kind == null) {
			return Specification.unrestricted();
		}
		if (DELIVERY.equals(kind)) {
			return (root, query, cb) -> cb.isNull(root.get("parentBatchId"));
		}
		Set<Long> machineLines = queries.linesWithMachine();
		return (root, query, cb) -> machineLines.isEmpty() ? cb.conjunction()
			: cb.or(cb.isNotNull(root.get("parentBatchId")),
				cb.not(root.get("productionLineId").in(machineLines)));
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
