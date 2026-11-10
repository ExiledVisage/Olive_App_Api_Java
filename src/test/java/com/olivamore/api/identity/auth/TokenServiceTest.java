package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.security.IdentityWebConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Refresh token ozeti ve Laravel'den kalan {@code $2y$} sifre uyumu. */
class TokenServiceTest {

	@Test
	void storedHashIsSha256OfThePlainToken() {
		assertThat(TokenService.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void passwordsAreLaravelCompatible() {
		String hash = AuthService.hash("123");
		assertThat(hash).startsWith("$2y$12$");
		assertThat(IdentityWebConfig.PASSWORDS.matches("123", hash)).isTrue();
	}

	@Test
	void passwordsLongerThan72BytesAreTruncatedLikePhp() {
		String first72 = "a".repeat(72);
		String hash = AuthService.hash(first72 + "fazlasi");
		assertThat(hash).startsWith("$2y$12$");
		assertThat(IdentityWebConfig.PASSWORDS.matches(first72, hash)).isTrue();
		assertThat(IdentityWebConfig.PASSWORDS.matches(first72 + "baskasi", hash)).isTrue();
		assertThat(IdentityWebConfig.PASSWORDS.matches("a".repeat(71) + "b", hash)).isFalse();
		// cok baytli karakter sinirda bolunse de hata vermez
		assertThat(AuthService.hash("ş".repeat(50))).startsWith("$2y$12$");
	}

}
