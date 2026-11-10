package com.olivamore.api.identity.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

	boolean existsByName(String name);

	List<Permission> findAllByOrderByNameAsc();

	List<Permission> findByNameIn(List<String> names);

}
