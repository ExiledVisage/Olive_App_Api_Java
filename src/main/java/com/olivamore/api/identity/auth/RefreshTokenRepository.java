package com.olivamore.api.identity.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	/** Ayni token'la gelen eszamanli yenilemeler sirayla islenir. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RefreshToken r where r.tokenHash = :hash")
	Optional<RefreshToken> findForUpdate(String hash);

}
