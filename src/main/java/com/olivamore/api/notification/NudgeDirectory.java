package com.olivamore.api.notification;

import com.olivamore.api.notification.NotificationResponses.NudgeTarget;
import com.olivamore.api.notification.NotificationResponses.Sender;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Durtulen isler ve kullanicilar: diger modullerin tablolarindan salt okunur. */
@Component
class NudgeDirectory {

	private static final String HAS_ROLE = """
		exists (select 1 from user_roles m join roles r on r.id = m.role_id
			where m.user_id = u.id and r.name in (:roles))
		""";

	private record UserRow(Long id, String name, String email) {
	}

	private record RoleRow(Long userId, String name) {
	}

	private final JdbcClient jdbc;

	NudgeDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Isin bildirimde gorunen adi; kayit yoksa bos. */
	Optional<String> label(NudgeSubject subject, long id) {
		return jdbc.sql(labelSql(subject)).param("id", id).query(String.class).optional();
	}

	private static String labelSql(NudgeSubject subject) {
		return switch (subject) {
			case FINISHED_PRODUCT_RUN -> """
				select coalesce(p.name, 'Ürün') || ' - ' || r.quantity || ' adet'
				from finished_product_runs r left join finished_products p on p.id = r.finished_product_id
				where r.id = :id""";
			case ORDER -> """
				select 'Sipariş #' || o.id || ' - ' || coalesce(c.name, 'Müşteri')
				from orders o left join customers c on c.id = o.customer_id where o.id = :id""";
			case FINISHED_PRODUCT -> "select name from finished_products where id = :id";
			case PRODUCTION_BATCH -> "select batch_code from production_batches where id = :id";
			case BATCH_PROCESS -> """
				select coalesce(b.batch_code, 'Parti') || ' - ' || coalesce(s.name, 'Aşama')
				from batch_processes p
				left join production_batches b on b.id = p.production_batch_id
				left join production_stages s on s.id = p.production_stage_id
				where p.id = :id""";
		};
	}

	boolean userExists(long id) {
		return jdbc.sql("select count(*) from users where id = ?").param(id).query(Long.class).single() > 0;
	}

	/** Roldeki kullanicilar, {@code exceptUserId} haric. */
	List<Long> usersWithRole(String role, long exceptUserId) {
		return jdbc.sql("select u.id from users u where " + HAS_ROLE + " and u.id <> :except order by u.id")
			.param("roles", List.of(role))
			.param("except", exceptUserId)
			.query(Long.class)
			.list();
	}

	Map<Long, Sender> senders(Collection<Long> userIds) {
		List<Long> present = ValidationErrors.present(userIds);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name from users where id in (:ids)")
			.param("ids", present)
			.query(Sender.class)
			.list().stream()
			.collect(Collectors.toMap(Sender::id, Function.identity()));
	}

	/** Sorumlu rollerdeki kullanicilar (durten haric), ada gore, rolleriyle. */
	List<NudgeTarget> targets(long actorId) {
		List<UserRow> users = jdbc.sql(
				"select u.id, u.name, u.email from users u where " + HAS_ROLE + " and u.id <> :except order by u.name")
			.param("roles", NudgeSubject.roles())
			.param("except", actorId)
			.query(UserRow.class)
			.list();
		if (users.isEmpty()) {
			return List.of();
		}
		Map<Long, List<String>> roles = jdbc.sql("""
				select m.user_id, r.name from roles r join user_roles m on m.role_id = r.id
				where m.user_id in (:ids) order by r.id
				""")
			.param("ids", users.stream().map(UserRow::id).toList())
			.query(RoleRow.class)
			.list().stream()
			.collect(Collectors.groupingBy(RoleRow::userId,
				Collectors.mapping(RoleRow::name, Collectors.toList())));
		return users.stream()
			.map(user -> new NudgeTarget(user.id(), user.name(), user.email(), roles.getOrDefault(user.id(), List.of())))
			.toList();
	}

}
