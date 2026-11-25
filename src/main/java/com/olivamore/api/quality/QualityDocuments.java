package com.olivamore.api.quality;

import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * BRC/IFS dokuman kaydi (docs/QUALITY.md 4). Sonraki gozden gecirme son gozden gecirme + siklik; hic gozden
 * gecirilmemis yururlukteki dokuman gecikmis sayilir. Kaldirilan dokuman takip edilmez.
 */
@Service
class QualityDocuments {

	static final int SOON_DAYS = 30;

	static final Set<String> KINDS = Set.of("procedure", "instruction", "form", "plan", "policy", "record");

	static final Set<String> STATUSES = Set.of("draft", "active", "obsolete");

	record Owner(Long id, String name) {
	}

	/** @param reviewState overdue, soon, ok; taslak ve kaldirilmista null */
	record Document(long id, String code, String title, String kind, String clause, String version, Owner owner,
		String status, int reviewMonths, LocalDate lastReviewedAt, LocalDate nextReviewAt, String reviewState,
		String link, String note) {
	}

	record DocumentRequest(String code, String title, String kind, String clause, String version, Long ownerUserId,
		String status, Integer reviewMonths, LocalDate lastReviewedAt, String link, String note) {
	}

	record ReviewRequest(String version, String note) {
	}

	private record DocumentRow(long id, String code, String title, String kind, String clause, String version,
		Long ownerUserId, String ownerName, String status, int reviewMonths, LocalDate lastReviewedAt, String link,
		String note) {

		Document document(LocalDate today) {
			LocalDate next = lastReviewedAt == null ? null : lastReviewedAt.plusMonths(reviewMonths);
			String state = null;
			if ("active".equals(status)) {
				state = next == null || next.isBefore(today) ? "overdue"
					: next.isAfter(today.plusDays(SOON_DAYS)) ? "ok" : "soon";
			}
			return new Document(id, code, title, kind, clause, version, ownerUserId == null ? null
				: new Owner(ownerUserId, ownerName), status, reviewMonths, lastReviewedAt, next, state, link, note);
		}

	}

	private static final String SELECT = """
		select d.id, d.code, d.title, d.kind, d.clause, d.version, d.owner_user_id, u.name as owner_name, d.status,
			d.review_months, d.last_reviewed_at, d.link, d.note
		from quality_documents d left join users u on u.id = d.owner_user_id
		""";

	private final JdbcClient jdbc;

	QualityDocuments(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Koda gore; {@code state} verilirse yalnizca o gozden gecirme durumu. */
	@Transactional(readOnly = true)
	List<Document> all(String status, String state) {
		LocalDate today = Timestamps.now().toLocalDate();
		return jdbc.sql(SELECT + " where (cast(:status as varchar) is null or d.status = :status) order by d.code")
			.param("status", status).query(DocumentRow.class).list().stream().map(r -> r.document(today))
			.filter(d -> state == null || state.equals(d.reviewState())).toList();
	}

	@Transactional(readOnly = true)
	Document get(long id) {
		return jdbc.sql(SELECT + " where d.id = :id").param("id", id).query(DocumentRow.class).optional()
			.map(r -> r.document(Timestamps.now().toLocalDate()))
			.orElseThrow(() -> new ModelNotFoundException("QualityDocument", id));
	}

	@Transactional
	Document create(DocumentRequest request) {
		validate(request, null);
		LocalDateTime now = Timestamps.now();
		long id = jdbc.sql("""
			insert into quality_documents (code, title, kind, clause, version, owner_user_id, status, review_months,
				last_reviewed_at, link, note, created_at, updated_at)
			values (:code, :title, :kind, :clause, :version, :owner, :status, :months, :reviewed, :link, :note, :now,
				:now)
			returning id
			""").param("code", request.code().trim()).param("title", request.title().trim())
			.param("kind", request.kind()).param("clause", blank(request.clause())).param("version",
				blank(request.version())).param("owner", request.ownerUserId())
			.param("status", request.status() == null ? "active" : request.status())
			.param("months", request.reviewMonths() == null ? 12 : request.reviewMonths())
			.param("reviewed", request.lastReviewedAt()).param("link", blank(request.link()))
			.param("note", blank(request.note())).param("now", now).query(Long.class).single();
		return get(id);
	}

	/** Verilmeyen alan degismez. */
	@Transactional
	Document update(long id, DocumentRequest request) {
		Document current = get(id);
		DocumentRequest merged = new DocumentRequest(
			request.code() != null ? request.code() : current.code(),
			request.title() != null ? request.title() : current.title(),
			request.kind() != null ? request.kind() : current.kind(),
			request.clause() != null ? request.clause() : current.clause(),
			request.version() != null ? request.version() : current.version(),
			request.ownerUserId() != null ? request.ownerUserId()
				: current.owner() == null ? null : current.owner().id(),
			request.status() != null ? request.status() : current.status(),
			request.reviewMonths() != null ? request.reviewMonths() : current.reviewMonths(),
			request.lastReviewedAt() != null ? request.lastReviewedAt() : current.lastReviewedAt(),
			request.link() != null ? request.link() : current.link(),
			request.note() != null ? request.note() : current.note());
		validate(merged, id);
		jdbc.sql("""
			update quality_documents set code = :code, title = :title, kind = :kind, clause = :clause,
				version = :version, owner_user_id = :owner, status = :status, review_months = :months,
				last_reviewed_at = :reviewed, link = :link, note = :note, updated_at = :now
			where id = :id
			""").param("code", merged.code().trim()).param("title", merged.title().trim()).param("kind", merged.kind())
			.param("clause", blank(merged.clause())).param("version", blank(merged.version()))
			.param("owner", merged.ownerUserId()).param("status", merged.status())
			.param("months", merged.reviewMonths()).param("reviewed", merged.lastReviewedAt())
			.param("link", blank(merged.link())).param("note", blank(merged.note())).param("now", Timestamps.now())
			.param("id", id).update();
		return get(id);
	}

	/** Bugun gozden gecirildi; surum verilirse degisir. */
	@Transactional
	Document review(long id, ReviewRequest request) {
		Document current = get(id);
		jdbc.sql("""
			update quality_documents set last_reviewed_at = :today, version = :version,
				note = coalesce(:note, note), updated_at = :now
			where id = :id
			""").param("today", Timestamps.now().toLocalDate())
			.param("version", request.version() != null && !request.version().isBlank() ? request.version().trim()
				: current.version())
			.param("note", blank(request.note())).param("now", Timestamps.now()).param("id", id).update();
		return get(id);
	}

	@Transactional
	void delete(long id) {
		get(id);
		jdbc.sql("delete from quality_documents where id = :id").param("id", id).update();
	}

	private void validate(DocumentRequest request, Long id) {
		ValidationErrors errors = new ValidationErrors();
		if (blank(request.code()) == null) {
			errors.add("code", MessageKeys.REQUIRED);
		}
		else if (request.code().trim().length() > 50) {
			errors.add("code", "Kod en fazla 50 karakter olabilir.");
		}
		else if (jdbc.sql("select count(*) from quality_documents where lower(code) = lower(:code) and id <> :id")
			.param("code", request.code().trim()).param("id", id == null ? -1 : id).query(Long.class).single() > 0) {
			errors.add("code", "Bu kodla bir doküman zaten var.");
		}
		if (blank(request.title()) == null) {
			errors.add("title", MessageKeys.REQUIRED);
		}
		if (request.kind() == null || !KINDS.contains(request.kind())) {
			errors.add("kind", MessageKeys.IN);
		}
		if (request.status() != null && !STATUSES.contains(request.status())) {
			errors.add("status", MessageKeys.IN);
		}
		if (request.reviewMonths() != null && (request.reviewMonths() < 1 || request.reviewMonths() > 60)) {
			errors.add("review_months", "Gözden geçirme sıklığı 1-60 ay olmalıdır.");
		}
		if (request.lastReviewedAt() != null && request.lastReviewedAt().isAfter(Timestamps.now().toLocalDate())) {
			errors.add("last_reviewed_at", "Gözden geçirme tarihi ileri bir tarih olamaz.");
		}
		if (request.ownerUserId() != null && jdbc.sql("select count(*) from users where id = :id")
			.param("id", request.ownerUserId()).query(Long.class).single() == 0) {
			errors.add("owner_user_id", MessageKeys.EXISTS);
		}
		errors.throwIfAny();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
