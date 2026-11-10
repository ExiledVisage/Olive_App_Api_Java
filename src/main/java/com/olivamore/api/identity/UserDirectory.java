package com.olivamore.api.identity;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Kullanici adlari ve rol uyelikleri: diger modullerin okudugu tek kapi. */
@Component
public class UserDirectory {

	public record User(Long id, String name, String email) {
	}

	public record UserWithRoles(Long id, String name, String email, List<String> roles) {
	}

	private record RoleRow(Long userId, String name) {
	}

	private static final String HAS_ROLE = """
		exists (select 1 from user_roles m join roles r on r.id = m.role_id
			where m.user_id = u.id and r.name in (:roles))
		""";

	private final JdbcClient jdbc;

	UserDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean exists(long id) {
		return jdbc.sql("select count(*) from users where id = :id").param("id", id).query(Long.class).single() > 0;
	}

	/** Bulunamayan id'ler haritada yok. */
	public Map<Long, User> users(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, email from users where id in (:ids)").param("ids", present)
			.query(User.class).list().stream().collect(Collectors.toMap(User::id, Function.identity()));
	}

	/** Roldeki kullanicilar, {@code exceptUserId} haric, id sirasinda. */
	public List<Long> idsWithRole(String role, long exceptUserId) {
		return jdbc.sql("select u.id from users u where " + HAS_ROLE + " and u.id <> :except order by u.id")
			.param("roles", List.of(role)).param("except", exceptUserId).query(Long.class).list();
	}

	/** Rollerden birine sahip kullanicilar, {@code exceptUserId} haric, ada gore; roller id sirasinda. */
	public List<UserWithRoles> withAnyRole(Collection<String> roles, long exceptUserId) {
		List<User> users = jdbc.sql(
				"select u.id, u.name, u.email from users u where " + HAS_ROLE + " and u.id <> :except order by u.name")
			.param("roles", roles).param("except", exceptUserId).query(User.class).list();
		if (users.isEmpty()) {
			return List.of();
		}
		Map<Long, List<String>> byUser = jdbc.sql("""
				select m.user_id, r.name from roles r join user_roles m on m.role_id = r.id
				where m.user_id in (:ids) order by r.id
				""")
			.param("ids", users.stream().map(User::id).toList()).query(RoleRow.class).list().stream()
			.collect(Collectors.groupingBy(RoleRow::userId, Collectors.mapping(RoleRow::name, Collectors.toList())));
		return users.stream().map(user -> new UserWithRoles(user.id(), user.name(), user.email(),
			byUser.getOrDefault(user.id(), List.of()))).toList();
	}

}
