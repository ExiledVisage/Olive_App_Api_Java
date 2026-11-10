package com.olivamore.api.identity.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, long id);

	List<Role> findAllByOrderByNameAsc();

	List<Role> findByNameIn(List<String> names);

	@Query("select p.name from Role r join r.permissions p where r.id = :roleId order by p.id")
	List<String> permissionNames(@Param("roleId") long roleId);

}
