package com.olivamore.api.attendance;

import com.olivamore.api.shared.error.BusinessRuleException;
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
import java.util.Optional;
import java.util.Set;

/**
 * Giris-cikis kayitlari (docs/ATTENDANCE.md): QR okutma, iceridekiler, yonetici kaydi ve iptal. Okutmada giris mi
 * cikis mi kisinin son gecerli kaydindan cikar: icerideyse cikis, disaridaysa (ya da acik giris unutulmussa) giris.
 */
@Service
class Attendance {

	record User(long id, String name) {
	}

	/** @param duplicate ayni kisi az once okutulmustu; yeni kayit acilmadi */
	record ScanResult(User user, String kind, LocalDateTime occurredAt, boolean duplicate) {
	}

	/** @param since iceride ise son giris zamani */
	record MyCode(String code, LocalDateTime expiresAt, long ttlSeconds, boolean inside, LocalDateTime since) {
	}

	record Inside(User user, LocalDateTime since) {
	}

	record Event(long id, User user, String kind, LocalDateTime occurredAt, String source, User recordedBy,
		String deviceLabel, String note, LocalDateTime voidedAt, User voidedBy, String voidReason) {
	}

	record ScanRequest(String code, String deviceLabel) {
	}

	record ManualRequest(Long userId, String kind, LocalDateTime occurredAt, String note) {
	}

	record VoidRequest(String reason) {
	}

	private record Last(long id, String kind, LocalDateTime occurredAt) {
	}

	private record EventRow(long id, long userId, String userName, String kind, LocalDateTime occurredAt,
		String source, Long recordedById, String recordedByName, String deviceLabel, String note,
		LocalDateTime voidedAt, Long voidedById, String voidedByName, String voidReason) {

		Event event() {
			return new Event(id, new User(userId, userName), kind, occurredAt, source,
				recordedById == null ? null : new User(recordedById, recordedByName), deviceLabel, note, voidedAt,
				voidedById == null ? null : new User(voidedById, voidedByName), voidReason);
		}

	}

	private static final String SELECT = """
		select e.id, e.user_id, u.name as user_name, e.kind, e.occurred_at, e.source,
			e.recorded_by_user_id as recorded_by_id, r.name as recorded_by_name, e.device_label, e.note,
			e.voided_at, e.voided_by_user_id as voided_by_id, v.name as voided_by_name, e.void_reason
		from attendance_events e
		join users u on u.id = e.user_id
		left join users r on r.id = e.recorded_by_user_id
		left join users v on v.id = e.voided_by_user_id
		""";

	private final JdbcClient jdbc;
	private final AttendanceCodes codes;
	private final AttendanceSettings settings;

	Attendance(JdbcClient jdbc, AttendanceCodes codes, AttendanceSettings settings) {
		this.jdbc = jdbc;
		this.codes = codes;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	MyCode myCode(long userId) {
		AttendanceCodes.Issued issued = codes.issue(userId);
		Optional<Last> open = openEntry(userId, Timestamps.now());
		return new MyCode(issued.code(), issued.expiresAt(), settings.codeTtl().toSeconds(), open.isPresent(),
			open.map(Last::occurredAt).orElse(null));
	}

	@Transactional
	ScanResult scan(ScanRequest request, long scannedBy) {
		long userId = codes.verify(request.code());
		User user = user(userId);
		LocalDateTime now = Timestamps.now();
		Optional<Last> last = last(userId);
		if (last.isPresent() && !last.get().occurredAt().plus(settings.duplicateWindow()).isBefore(now)) {
			return new ScanResult(user, last.get().kind(), last.get().occurredAt(), true);
		}
		String kind = openEntry(userId, now).isPresent() ? "out" : "in";
		insert(userId, kind, now, "scan", scannedBy, blank(request.deviceLabel()), null);
		return new ScanResult(user, kind, now, false);
	}

	/** Su an iceride olanlar: son gecerli kaydi giris ve unutulmus sayilmayacak kadar yeni. */
	@Transactional(readOnly = true)
	List<Inside> inside() {
		LocalDateTime since = Timestamps.now().minus(settings.forgottenAfter());
		return jdbc.sql("""
			select u.id, u.name, e.occurred_at from (
				select distinct on (user_id) user_id, kind, occurred_at from attendance_events
				where voided_at is null order by user_id, occurred_at desc, id desc
			) e join users u on u.id = e.user_id
			where e.kind = 'in' and e.occurred_at >= :since
			order by e.occurred_at
			""").param("since", since)
			.query((rs, i) -> new Inside(new User(rs.getLong("id"), rs.getString("name")),
				rs.getTimestamp("occurred_at").toLocalDateTime())).list();
	}

	/** Yerel gun araligindaki kayitlar (iptaller dahil), eskiden yeniye. */
	@Transactional(readOnly = true)
	List<Event> events(Long userId, LocalDate from, LocalDate to) {
		return jdbc.sql(SELECT + """
			 where (cast(:user as bigint) is null or e.user_id = :user)
				and e.occurred_at >= :from and e.occurred_at < :to
			order by e.occurred_at, e.id
			""").param("user", userId).param("from", settings.dayStartUtc(from))
			.param("to", settings.dayStartUtc(to.plusDays(1))).query(EventRow.class).list().stream()
			.map(EventRow::event).toList();
	}

	@Transactional
	Event record(ManualRequest request, long recordedBy) {
		ValidationErrors errors = new ValidationErrors();
		if (request.userId() == null) {
			errors.add("user_id", MessageKeys.REQUIRED);
		}
		if (request.kind() == null || !Set.of("in", "out").contains(request.kind())) {
			errors.add("kind", MessageKeys.IN);
		}
		if (request.occurredAt() == null) {
			errors.add("occurred_at", MessageKeys.REQUIRED);
		} else if (request.occurredAt().isAfter(Timestamps.now().plusMinutes(5))) {
			errors.add("occurred_at", "İleri tarihli kayıt girilemez.");
		}
		if (blank(request.note()) == null) {
			errors.add("note", "Elle girilen kayıtta gerekçe zorunludur.");
		}
		errors.throwIfAny();
		user(request.userId());
		long id = insert(request.userId(), request.kind(), request.occurredAt(), "manual", recordedBy, null,
			request.note().trim());
		return event(id);
	}

	@Transactional
	Event voidEvent(long id, VoidRequest request, long voidedBy) {
		Event event = event(id);
		if (event.voidedAt() != null) {
			throw new BusinessRuleException("Bu kayıt zaten iptal edilmiş.");
		}
		String reason = request == null ? null : blank(request.reason());
		if (reason == null) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("reason", "İptal gerekçesi zorunludur.");
			errors.throwIfAny();
		}
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			update attendance_events set voided_at = :now, voided_by_user_id = :by, void_reason = :reason,
				updated_at = :now where id = :id
			""").param("now", now).param("by", voidedBy).param("reason", reason).param("id", id).update();
		return event(id);
	}

	private Event event(long id) {
		return jdbc.sql(SELECT + " where e.id = :id").param("id", id).query(EventRow.class).optional()
			.map(EventRow::event).orElseThrow(() -> new ModelNotFoundException("AttendanceEvent", id));
	}

	private long insert(long userId, String kind, LocalDateTime at, String source, Long by, String device,
		String note) {
		LocalDateTime now = Timestamps.now();
		return jdbc.sql("""
			insert into attendance_events (user_id, kind, occurred_at, source, recorded_by_user_id, device_label, note,
				created_at, updated_at)
			values (:user, :kind, :at, :source, :by, :device, :note, :now, :now)
			returning id
			""").param("user", userId).param("kind", kind).param("at", at).param("source", source).param("by", by)
			.param("device", device).param("note", note).param("now", now).query(Long.class).single();
	}

	private Optional<Last> last(long userId) {
		return jdbc.sql("""
			select id, kind, occurred_at from attendance_events
			where user_id = :user and voided_at is null order by occurred_at desc, id desc limit 1
			""").param("user", userId).query(Last.class).optional();
	}

	/** Acik giris: son kayit giris ve unutulmus sayilmayacak kadar yeni. */
	private Optional<Last> openEntry(long userId, LocalDateTime now) {
		return last(userId).filter(l -> "in".equals(l.kind())
			&& !l.occurredAt().isBefore(now.minus(settings.forgottenAfter())));
	}

	private User user(long id) {
		return jdbc.sql("select id, name from users where id = :id").param("id", id).query(User.class).optional()
			.orElseThrow(() -> new BusinessRuleException("Kullanıcı bulunamadı."));
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
