package com.olivamore.api.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessTokenRepository extends JpaRepository<AccessToken, Long> {

	Optional<AccessToken> findFirstByToken(String token);

}
