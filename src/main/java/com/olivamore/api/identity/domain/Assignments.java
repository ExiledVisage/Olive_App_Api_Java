package com.olivamore.api.identity.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Spatie pivot tablolari: {@code model_has_roles}, {@code model_has_permissions},
 * {@code role_has_permissions}. Morph kolonu ({@code model_type}) JPA iliskilerine uymadigi icin SQL ile.
 * <p>
 * Siralar: Spatie'nin {@code getAllPermissions()}'i izinleri id'ye gore siralar (Laravel'de de
 * deterministik). Rollerin ve bir rolun izinlerinin sirasi Laravel'de tanimsiz (ORDER BY yok);
 * burada id sirasi.
 */
@Component
public class Assignments {

	private static final String USER = User.MORPH_TYPE;

	private final JdbcTemplate jdbc;

	public Assignments(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<String> roleNames(long userId) {
		return jdbc.queryForList("""
			select r.name from roles r join model_has_roles m on m.role_id = r.id
			where m.model_type = ? and m.model_id = ? order by r.id
			""", String.class, USER, userId);
	}

	public boolean hasAnyRole(long userId, List<String> roles) {
		return roleNames(userId).stream().anyMatch(roles::contains);
	}

	/** {@code getAllPermissions()}: dogrudan + rollerden gelen, tekrarsiz, id sirasinda. */
	public List<String> permissionNames(long userId) {
		return jdbc.queryForList("""
			select p.name from permissions p where p.id in (
			    select permission_id from model_has_permissions where model_type = ? and model_id = ?
			    union
			    select rp.permission_id from role_has_permissions rp
			    join model_has_roles m on m.role_id = rp.role_id
			    where m.model_type = ? and m.model_id = ?)
			order by p.id
			""", String.class, USER, userId, USER, userId);
	}

	public List<String> rolePermissionNames(long roleId) {
		return jdbc.queryForList("""
			select p.name from permissions p join role_has_permissions rp on rp.permission_id = p.id
			where rp.role_id = ? order by p.id
			""", String.class, roleId);
	}

	/** {@code $user->syncRoles(...)} */
	public void syncUserRoles(long userId, List<Long> roleIds) {
		jdbc.update("delete from model_has_roles where model_type = ? and model_id = ?", USER, userId);
		for (Long roleId : new LinkedHashSet<>(roleIds)) {
			jdbc.update("insert into model_has_roles (role_id, model_type, model_id) values (?, ?, ?)", roleId, USER, userId);
		}
		forgetLaravelPermissionCache();
	}

	/** {@code $role->syncPermissions(...)} */
	public void syncRolePermissions(long roleId, List<Long> permissionIds) {
		jdbc.update("delete from role_has_permissions where role_id = ?", roleId);
		for (Long permissionId : new LinkedHashSet<>(permissionIds)) {
			jdbc.update("insert into role_has_permissions (permission_id, role_id) values (?, ?)", permissionId, roleId);
		}
		forgetLaravelPermissionCache();
	}

	/** Spatie HasRoles: kullanici silinirken rolleri ve dogrudan izinleri kopar. */
	public void detachUser(long userId) {
		jdbc.update("delete from model_has_roles where model_type = ? and model_id = ?", USER, userId);
		jdbc.update("delete from model_has_permissions where model_type = ? and model_id = ?", USER, userId);
		forgetLaravelPermissionCache();
	}

	/**
	 * GECIS DONEMI: Laravel izinleri 24 saat onbellekte tutar ({@code cache} tablosu) ve onbellegi
	 * yalnizca kendi model olaylarinda temizler. Java'nin yaptigi degisiklikleri Laravel'in hemen
	 * gormesi icin onbellek silinir. Laravel kapaninca (B1) {@code cache} tablosuyla birlikte kalkar.
	 */
	public void forgetLaravelPermissionCache() {
		jdbc.update("delete from cache where key like '%spatie.permission.cache'");
	}

}
