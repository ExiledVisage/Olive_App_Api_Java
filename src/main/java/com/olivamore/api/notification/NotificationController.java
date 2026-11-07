package com.olivamore.api.notification;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code NotificationController} + {@code NotificationService}. Bildirimler kullanicinin kendisine ait
 * (ayri izin yok); durtme {@code notification.send} ister.
 */
@RestController
class NotificationController {

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
	private final LaravelValidator validator;

	NotificationController(Db db, LaravelValidator validator) {
		this.db = db;
		this.validator = validator;
	}

	// ---- kullanicinin bildirimleri -------------------------------------------------

	/** Her zaman sayfali ({@code per_page} varsayilan 30, en fazla 100); {@code ?unread=1} yalnizca okunmamislar; en yeni once. */
	@GetMapping("/api/notifications")
	ListResponse<Map<String, Object>> index(HttpServletRequest request, RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("unread", "nullable|boolean")
			.field("per_page", "nullable|integer|min:1|max:100"));
		boolean unread = v.get("unread") != null && Boolean.TRUE.equals(com.olivamore.api.shared.persistence.Casts.bool(v.get("unread")));
		int perPage = v.get("per_page") == null ? 30 : (int) Php.intval(v.get("per_page"));
		String where = " where user_id = ?" + (unread ? " and read_at is null" : "");
		int page = Listing.currentPage(request);
		long total = db.count("select count(*) as aggregate from user_notifications" + where, user.id());
		List<Row> rows = total == 0 ? List.of() : db.select("select * from user_notifications" + where + " order by created_at desc"
			+ " limit " + perPage + " offset " + (long) (page - 1) * perPage, user.id());
		// Laravel paginate() withQueryString'siz: linklerde unread/per_page yok
		return LaravelPaginator.paginateWithoutQueryString(resources(rows, true), total, page, perPage, request);
	}

	/** Zil ikonu: okunmamis sayisi. */
	@GetMapping("/api/notifications/unread-count")
	Map<String, Object> unreadCount(CurrentUser user) {
		return Map.of("data", Map.of("unread",
			db.count("select count(*) from user_notifications where user_id = ? and read_at is null", user.id())));
	}

	/** Baskasinin bildirimi okunmus sayilamaz. */
	@PostMapping("/api/notifications/{notification}/read")
	@Transactional
	Map<String, Object> markRead(@Bound(value = "notification", table = "user_notifications", model = "UserNotification") Row notification,
		CurrentUser user) {
		if (Php.intval(notification.get("user_id")) != user.id()) {
			throw new BusinessRuleException("Bu bildirim size ait değil.");
		}
		if (notification.get("read_at") == null) {
			db.update("user_notifications", notification.id(), Map.of("read_at", Timestamps.now()));
		}
		return Map.of("data", resources(List.of(db.find("user_notifications", notification.id()).orElseThrow()), true).getFirst());
	}

	@PostMapping("/api/notifications/read-all")
	@Transactional
	Map<String, Object> markAllRead(CurrentUser user) {
		LocalDateTime now = Timestamps.now();
		int marked = db.execute("update user_notifications set read_at = ?, updated_at = ? where user_id = ? and read_at is null",
			now, now, user.id());
		return Map.of("data", Map.of("marked", marked));
	}

	// ---- durtme (notification.send) --------------------------------------------------

	/**
	 * Isciyi durt (201): varsayilan olarak isin sorumlu rolundeki herkese (durten haric), {@code user_id}
	 * verilirse yalnizca o kisiye. Ayni is icin ayni kisiye 3 dakika icinde ikinci uyari yok.
	 */
	@PostMapping("/api/nudges")
	@RequiresPermission("notification.send")
	@Transactional
	ResponseEntity<Map<String, Object>> nudge(RequestInput input, CurrentUser actor) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("subject_type", "required|string|in:" + String.join(",", SUBJECTS.keySet()))
			.field("subject_id", "required|integer|min:1")
			.field("user_id", "nullable|integer|exists:users,id")
			.field("note", "nullable|string|max:500"));
		String subjectType = Php.strval(v.get("subject_type"));
		long subjectId = Php.intval(v.get("subject_id"));
		Subject definition = SUBJECTS.get(subjectType);
		if (definition == null) {
			throw new BusinessRuleException("Bu iş için uyarı gönderilemez.");
		}
		Row subject = db.find(definition.table(), subjectId).orElseThrow(() -> new BusinessRuleException("Uyarı gönderilecek kayıt bulunamadı."));
		List<Row> recipients = recipients(actor, definition.role(), v.get("user_id"));
		assertNotJustNudged(subjectType, subjectId, recipients);
		String body = body(subjectType, subject, v.get("note"));
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
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("sent_to", created.size());
		data.put("notifications", resources(created, false));
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
	}

	/** "Belirli bir kisiyi uyar" listesi: sorumlu rollerdeki kullanicilar (durten haric), ada gore, rolleriyle. */
	@GetMapping("/api/nudges/targets")
	@RequiresPermission("notification.send")
	Map<String, Object> nudgeTargets(CurrentUser actor) {
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
		return Map.of("data", out);
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

	/** UserNotificationResource. @param withSender {@code sentByUser} yuklu mu */
	private List<Map<String, Object>> resources(List<Row> rows, boolean withSender) {
		Map<Long, Row> senders = withSender
			? Relations.byId(db.whereIn("users", "id", Relations.column(rows, "sent_by_user_id"), null))
			: Map.of();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row n : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", n.get("id"));
			r.put("type", n.get("type"));
			r.put("title", n.get("title"));
			r.put("body", n.get("body"));
			r.put("subject_type", n.get("subject_type"));
			r.put("subject_id", n.get("subject_id") == null ? null : Php.intval(n.get("subject_id")));
			r.put("is_read", n.get("read_at") != null);
			r.put("read_at", n.get("read_at"));
			r.put("created_at", n.get("created_at"));
			if (withSender) {
				Row sender = n.longValue("sent_by_user_id") == null ? null : senders.get(n.longValue("sent_by_user_id"));
				if (sender == null) {
					r.put("sent_by", null);
				}
				else {
					Map<String, Object> s = new LinkedHashMap<>();
					s.put("id", sender.get("id"));
					s.put("name", sender.get("name"));
					r.put("sent_by", s);
				}
			}
			out.add(r);
		}
		return out;
	}

}
