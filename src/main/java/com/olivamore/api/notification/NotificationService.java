package com.olivamore.api.notification;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code NotificationService}: bildirimler ve "durtme". Bildirim simdilik yalnizca uygulama icinde
 * ({@code channel = in_app}); push sonradan eklenecek.
 */
@Service
class NotificationService {

	/** Ayni is icin ayni kisiye bu sure icinde ikinci kez durtme yok. */
	static final int NUDGE_COOLDOWN_MINUTES = 3;

	private static final String USER_MODEL = "App\\Models\\User";

	/** Durtulebilecek isler: tablo, varsayilan sorumlu rol, baslik (Laravel {@code SUBJECTS} sirasiyla). */
	private record Subject(String table, String role, String title) {
	}

	private static final Map<String, Subject> SUBJECTS = new LinkedHashMap<>();

	static {
		SUBJECTS.put("finished_product_run", new Subject("finished_product_runs", "warehouse_operator", "Dolum koşusu bekliyor"));
		SUBJECTS.put("order", new Subject("orders", "warehouse_operator", "Sipariş hazırlanmayı bekliyor"));
		SUBJECTS.put("finished_product", new Subject("finished_products", "warehouse_operator", "Ürün depoya konmayı bekliyor"));
		SUBJECTS.put("production_batch", new Subject("production_batches", "production_operator", "Parti üretimde bekliyor"));
		SUBJECTS.put("batch_process", new Subject("batch_processes", "admin", "Aşama onay bekliyor"));
	}

	private final Db db;

	NotificationService(Db db) {
		this.db = db;
	}

	static Set<String> subjectTypes() {
		return SUBJECTS.keySet();
	}

	/** {@code paginateFor}: kullanicinin bildirimleri (okunmamislar), en yeni once. @return [toplam, satirlar] */
	long count(long userId, boolean unread) {
		return db.count("select count(*) as aggregate from user_notifications" + where(unread), userId);
	}

	List<Row> page(long userId, boolean unread, int page, int perPage) {
		return db.select("select * from user_notifications" + where(unread) + " order by created_at desc"
			+ " limit " + perPage + " offset " + (long) (page - 1) * perPage, userId);
	}

	private static String where(boolean unread) {
		return " where user_id = ?" + (unread ? " and read_at is null" : "");
	}

	long unreadCount(long userId) {
		return db.count("select count(*) from user_notifications where user_id = ? and read_at is null", userId);
	}

	/** Baskasinin bildirimi okunmus sayilamaz. */
	Row markRead(Row notification, long userId) {
		if (Php.intval(notification.get("user_id")) != userId) {
			throw new BusinessRuleException("Bu bildirim size ait değil.");
		}
		if (notification.get("read_at") == null) {
			db.update("user_notifications", notification.id(), Map.of("read_at", Timestamps.now()));
		}
		return db.find("user_notifications", notification.id()).orElseThrow();
	}

	int markAllRead(long userId) {
		LocalDateTime now = Timestamps.now();
		return db.execute("update user_notifications set read_at = ?, updated_at = ? where user_id = ? and read_at is null",
			now, now, userId);
	}

	/**
	 * Isciyi durt: varsayilan olarak isin sorumlu rolundeki herkese (durten haric), {@code userId} verilirse
	 * yalnizca o kisiye. Ayni is icin ayni kisiye 3 dakika icinde ikinci uyari yok. @return olusan bildirimler
	 */
	List<Row> nudge(CurrentUser actor, String subjectType, long subjectId, Object userId, Object note) {
		Subject definition = SUBJECTS.get(subjectType);
		if (definition == null) {
			throw new BusinessRuleException("Bu iş için uyarı gönderilemez.");
		}
		Row subject = db.find(definition.table(), subjectId).orElseThrow(() -> new BusinessRuleException("Uyarı gönderilecek kayıt bulunamadı."));
		List<Row> recipients = recipients(actor, definition.role(), userId);
		assertNotJustNudged(subjectType, subjectId, recipients);
		String body = body(subjectType, subject, note);
		List<Row> created = new ArrayList<>();
		for (Row recipient : recipients) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("user_id", recipient.id());
			values.put("sent_by_user_id", actor.id());
			values.put("type", "nudge");
			values.put("channel", "in_app");
			values.put("title", definition.title());
			values.put("body", body);
			values.put("subject_type", subjectType);
			values.put("subject_id", subjectId);
			created.add(db.create("user_notifications", values));
		}
		return created;
	}

	/** Durtulebilecek kisiler: sorumlu rollerdeki kullanicilar (durten haric), ada gore, rolleriyle. */
	List<Map<String, Object>> nudgeTargets(CurrentUser actor) {
		List<String> roles = SUBJECTS.values().stream().map(Subject::role).distinct().toList();
		String marks = String.join(", ", roles.stream().map(r -> "?").toList());
		List<Object> args = new ArrayList<>(List.of(USER_MODEL));
		args.addAll(roles);
		args.add(actor.id());
		List<Row> users = db.select("select * from users where exists (select * from roles inner join model_has_roles"
			+ " on roles.id = model_has_roles.role_id where users.id = model_has_roles.model_id and model_has_roles.model_type = ?"
			+ " and name in (" + marks + ")) and id != ? order by name asc", args.toArray());
		Map<Long, List<String>> userRoles = new LinkedHashMap<>();
		if (!users.isEmpty()) {
			for (Row r : db.select("select roles.id, roles.name, model_has_roles.model_id as pivot_model_id from roles inner join model_has_roles"
				+ " on roles.id = model_has_roles.role_id where model_has_roles.model_id in (" + Relations.inList(Relations.ids(users))
				+ ") and model_has_roles.model_type = ?", USER_MODEL)) {
				userRoles.computeIfAbsent(r.longValue("pivot_model_id"), k -> new ArrayList<>()).add(r.string("name"));
			}
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row u : users) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", u.get("id"));
			m.put("name", u.get("name"));
			m.put("email", u.get("email"));
			m.put("roles", userRoles.getOrDefault(u.id(), List.of()));
			out.add(m);
		}
		return out;
	}

	// ---- yardimcilar ---------------------------------------------------------------------

	private List<Row> recipients(CurrentUser actor, String role, Object userId) {
		if (userId != null) {
			Row user = db.find("users", Php.intval(userId)).orElseThrow(() -> new BusinessRuleException("Uyarılacak kullanıcı bulunamadı."));
			if (user.id() == actor.id()) {
				throw new BusinessRuleException("Kendinizi uyaramazsınız.");
			}
			return List.of(user);
		}
		List<Row> users = db.select("select * from users where exists (select * from roles inner join model_has_roles"
			+ " on roles.id = model_has_roles.role_id where users.id = model_has_roles.model_id and model_has_roles.model_type = ?"
			+ " and roles.id in (select id from roles where name = ?)) and id != ?", USER_MODEL, role, actor.id());
		if (users.isEmpty()) {
			throw new BusinessRuleException("Bu iş '" + role + "' rolüne gidiyor ama o rolde uyarılacak kullanıcı yok.");
		}
		return users;
	}

	private void assertNotJustNudged(String subjectType, long subjectId, List<Row> recipients) {
		LocalDateTime since = Timestamps.now().minusMinutes(NUDGE_COOLDOWN_MINUTES);
		if (db.count("select count(*) from user_notifications where subject_type = ? and subject_id = ? and user_id in ("
			+ Relations.inList(Relations.ids(recipients)) + ") and created_at >= ?", subjectType, subjectId, since) > 0) {
			throw new BusinessRuleException("Bu iş için az önce uyarı gönderildi. " + NUDGE_COOLDOWN_MINUTES
				+ " dakika sonra tekrar deneyebilirsiniz.");
		}
	}

	/** Bildirim govdesi: hangi kayit oldugu, varsa not; aralarinda " • ". */
	private String body(String subjectType, Row subject, Object note) {
		String label = switch (subjectType) {
			case "finished_product_run" -> (nameOf("finished_products", subject.longValue("finished_product_id"), "name", "Ürün")
				+ " - " + Php.intval(subject.get("quantity")) + " adet").strip();
			case "order" -> ("Sipariş #" + subject.id() + " - " + nameOf("customers", subject.longValue("customer_id"), "name", "Müşteri")).strip();
			case "finished_product" -> subject.string("name");
			case "production_batch" -> subject.string("batch_code");
			case "batch_process" -> (nameOf("production_batches", subject.longValue("production_batch_id"), "batch_code", "Parti") + " - "
				+ nameOf("production_stages", subject.longValue("production_stage_id"), "name", "Aşama")).strip();
			default -> null;
		};
		String trimmed = note == null ? null : Php.strval(note).strip();
		List<String> parts = new ArrayList<>();
		for (String part : new String[] {label, trimmed}) {
			if (part != null && !part.isEmpty() && !"0".equals(part)) {
				parts.add(part);
			}
		}
		return String.join(" • ", parts);
	}

	private String nameOf(String table, Long id, String column, String fallback) {
		if (id == null) {
			return fallback;
		}
		return db.find(table, id).map(r -> r.get(column)).map(Php::strval).orElse(fallback);
	}

}
