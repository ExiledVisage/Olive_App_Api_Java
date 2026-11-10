package com.olivamore.api.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param privateKey      PKCS#8 PEM RSA ozel anahtari; acik anahtar bundan turetilir
 * @param keyId           JWT basligindaki {@code kid}
 * @param issuer          {@code iss}
 * @param accessTokenTtl  access token omru
 * @param refreshTokenTtl refresh token omru
 */
@ConfigurationProperties("olive.security.jwt")
public record JwtProperties(String privateKey, String keyId, String issuer, Duration accessTokenTtl,
	Duration refreshTokenTtl) {
}
