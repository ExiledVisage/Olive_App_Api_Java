package com.olivamore.api.identity.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

/** Sanctum token formati ve Bearer ayristirma, Laravel'in kodu ve uretilmis bir Laravel token'iyla. */
class TokenServiceTest {

	/** Laravel'in register'da urettigi token (src/test/resources/laravel/identity.json, "register basarili"). */
	private static final String LARAVEL_TOKEN = "3|6JXWArlL8RU1a8Pxkhyj7BwNjtGTijlFMxBUN3rDcebddd93";

	@Test
	void laravelTokenFollowsTheSameFormat() {
		String plain = LARAVEL_TOKEN.substring(LARAVEL_TOKEN.indexOf('|') + 1);
		assertThat(plain).hasSize(48).matches("[A-Za-z0-9]{40}[0-9a-f]{8}");
		assertThat(crc32(plain.substring(0, 40))).isEqualTo(plain.substring(40));
	}

	@Test
	void generatedTokenMatchesLaravelFormat() {
		TokenService service = new TokenService(null, null);
		for (int i = 0; i < 100; i++) {
			String plain = service.generateTokenString();
			assertThat(plain).matches("[A-Za-z0-9]{40}[0-9a-f]{8}");
			assertThat(crc32(plain.substring(0, 40))).isEqualTo(plain.substring(40));
		}
	}

	@Test
	void storedHashIsSha256OfThePlainToken() {
		assertThat(TokenService.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void bearerTokenParsing() {
		assertThat(TokenService.bearerToken("Bearer 1|abc")).isEqualTo("1|abc");
		assertThat(TokenService.bearerToken("Basic x, Bearer 1|abc")).isEqualTo("1|abc");
		assertThat(TokenService.bearerToken("Bearer 1|abc, foo")).isEqualTo("1|abc");
		assertThat(TokenService.bearerToken("bearer 1|abc")).isNull(); // Laravel buyuk B arar
		assertThat(TokenService.bearerToken(null)).isNull();
	}

	@Test
	void validBearerTokenNeedsNumericId() {
		assertThat(TokenService.isValidBearerToken("1|abc")).isTrue();
		assertThat(TokenService.isValidBearerToken("abc")).isTrue(); // id'siz: hash ile aranir
		assertThat(TokenService.isValidBearerToken("x|abc")).isFalse();
		assertThat(TokenService.isValidBearerToken("1|")).isFalse();
		assertThat(TokenService.isValidBearerToken("")).isFalse();
	}

	@Test
	void passwordsAreLaravelCompatible() {
		String hash = AuthService.hash("123");
		assertThat(hash).startsWith("$2y$12$");
		assertThat(IdentityWebConfig.PASSWORDS.matches("123", hash)).isTrue();
	}

	private static String crc32(String value) {
		CRC32 crc = new CRC32();
		crc.update(value.getBytes(StandardCharsets.US_ASCII));
		return String.format("%08x", crc.getValue());
	}

}
