package com.olivamore.api.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

	List<Permission> findAllByOrderByNameAsc();

	List<Permission> findByNameInAndGuardName(List<String> names, String guardName);

}
