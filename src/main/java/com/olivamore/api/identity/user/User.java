package com.olivamore.api.identity.user;

import com.olivamore.api.identity.permission.Permission;
import com.olivamore.api.identity.role.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column(name = "email_verified_at")
	private LocalDateTime emailVerifiedAt;

	@Column(nullable = false)
	private String password;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@ManyToMany
	@JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
		inverseJoinColumns = @JoinColumn(name = "role_id"))
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Set<Role> roles = new LinkedHashSet<>();

	@ManyToMany
	@JoinTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"),
		inverseJoinColumns = @JoinColumn(name = "permission_id"))
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Set<Permission> directPermissions = new LinkedHashSet<>();

	void replaceRoles(Collection<Role> newRoles) {
		roles.clear();
		roles.addAll(newRoles);
	}

}
