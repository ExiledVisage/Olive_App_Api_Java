package com.olivamore.api.identity.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

	private final SecurityConfig config = new SecurityConfig();

	@Test
	void applicationDoesNotStartWithoutAKey() {
		for (String key : new String[] {null, "", "  "}) {
			assertThatThrownBy(() -> config.jwtSigningKey(properties(key), new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_PRIVATE_KEY");
		}
	}

	@Test
	void onlyTheTestProfileGeneratesAKey() throws Exception {
		MockEnvironment test = new MockEnvironment();
		test.setActiveProfiles("test");

		assertThat(config.jwtSigningKey(properties(null), test).isPrivate()).isTrue();

		MockEnvironment production = new MockEnvironment();
		production.setActiveProfiles("prod");
		assertThatThrownBy(() -> config.jwtSigningKey(properties(null), production))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void pemKeyIsReadWithEscapedNewlinesAndKeepsItsId() throws Exception {
		RSAKey key = config.jwtSigningKey(properties(pem(2048).replace("\n", "\\n")), new MockEnvironment());

		assertThat(key.getKeyID()).isEqualTo("kid-1");
		assertThat(key.isPrivate()).isTrue();
		assertThat(key.toPublicJWK().toRSAPublicKey().getModulus()).isEqualTo(key.toRSAPrivateKey().getModulus());
	}

	@Test
	void weakKeyIsRefused() throws Exception {
		String weak = pem(1024);

		assertThatThrownBy(() -> config.jwtSigningKey(properties(weak), new MockEnvironment()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("2048");
	}

	private static JwtProperties properties(String privateKey) {
		return new JwtProperties(privateKey, "kid-1", "olive-api", Duration.ofMinutes(15), Duration.ofDays(30));
	}

	private static String pem(int bits) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(bits);
		String body = Base64.getMimeEncoder(64, "\n".getBytes())
			.encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
	}

}
