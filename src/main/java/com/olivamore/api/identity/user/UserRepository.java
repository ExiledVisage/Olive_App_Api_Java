package com.olivamore.api.identity.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, long id);

	/** Laravel {@code where('email', ...)}: PostgreSQL'de buyuk/kucuk harfe duyarli. */
	Optional<User> findFirstByEmail(String email);

	/** {@code User::query()->latest()->get()}: Laravel'in SQL'inin aynisi (esit created_at sirasi dahil). */
	@Query(value = "select * from users order by created_at desc", nativeQuery = true)
	List<User> latest();

	@Query("select r.name from User u join u.roles r where u.id = :userId order by r.id")
	List<String> roleNames(@Param("userId") long userId);

	@Query("""
		select p.name from Permission p
		where p.id in (select direct.id from User u join u.directPermissions direct where u.id = :userId)
			or p.id in (select viaRole.id from User u join u.roles r join r.permissions viaRole where u.id = :userId)
		order by p.id
		""")
	List<String> permissionNames(@Param("userId") long userId);

}
