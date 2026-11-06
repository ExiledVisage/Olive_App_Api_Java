package com.olivamore.api.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Laravel {@code where('email', ...)}: PostgreSQL'de buyuk/kucuk harfe duyarli. */
	Optional<User> findFirstByEmail(String email);

	/** {@code User::query()->latest()->get()}: Laravel'in SQL'inin aynisi (esit created_at sirasi dahil). */
	@Query(value = "select * from users order by created_at desc", nativeQuery = true)
	List<User> latest();

}
