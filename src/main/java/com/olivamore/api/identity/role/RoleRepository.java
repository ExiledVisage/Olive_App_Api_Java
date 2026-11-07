package com.olivamore.api.identity.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {

	List<Role> findAllByOrderByNameAsc();

	List<Role> findByNameInAndGuardName(List<String> names, String guardName);

}
