package com.olivamore.api.identity.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.olivamore.api.identity.security.JwtProperties;
import com.olivamore.api.shared.error.HttpStatusException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Oturumlar ve token'lar. Access token kisa omurlu RS256 JWT; refresh token rastgele, tek kullanimlik ve her
 * yenilemede degisir. Kullanilmis bir refresh token tekrar gelirse token calinmis sayilir, oturum iptal edilir.
 * <p>
 * Sureler gercek saatle hesaplanir: JWT dogrulamasi da onu kullanir.
 */
@Service
public class TokenService {

	/** Access token'daki oturum id'si. */
	public static final String SESSION_CLAIM = "sid";

	private static final int REFRESH_TOKEN_BYTES = 32;

	private final AuthSessionRepository sessions;
	private final RefreshTokenRepository refreshTokens;
	private final JwtEncoder encoder;
	private final RSAKey signingKey;
	private final JwtProperties properties;
	private final SecureRandom random = new SecureRandom();

	TokenService(AuthSessionRepository sessions, RefreshTokenRepository refreshTokens, JwtEncoder encoder,
		RSAKey signingKey, JwtProperties properties) {
		this.sessions = sessions;
		this.refreshTokens = refreshTokens;
		this.encoder = encoder;
		this.signingKey = signingKey;
		this.properties = properties;
	}

	/** Istemciye verilen token cifti. */
	record Tokens(String accessToken, String refreshToken, long expiresIn) {
	}

	/** Yeni giris: yeni oturum. */
	@Transactional
	Tokens issue(long userId) {
		Instant now = Instant.now();
		AuthSession session = sessions.save(new AuthSession(UUID.randomUUID(), userId, now));
		return tokens(session, now);
	}

	/** Oturum iptali, hata cevabina ragmen kalici olmali: {@code noRollbackFor}. */
	@Transactional(noRollbackFor = HttpStatusException.class)
	Tokens rotate(String refreshToken) {
		Instant now = Instant.now();
		RefreshToken current = refreshTokens.findForUpdate(sha256(refreshToken))
			.orElseThrow(HttpStatusException::unauthenticated);
		AuthSession session = sessions.findById(current.getSessionId())
			.orElseThrow(HttpStatusException::unauthenticated);
		if (current.isConsumed()) {
			session.revoke(now);
			throw HttpStatusException.unauthenticated();
		}
		if (session.isRevoked() || current.isExpired(now)) {
			throw HttpStatusException.unauthenticated();
		}
		current.consume(now);
		return tokens(session, now);
	}

	@Transactional
	void revoke(UUID sessionId) {
		sessions.findById(sessionId).ifPresent(session -> session.revoke(Instant.now()));
	}

	/** Her istekte: cikis yapilmis ya da iptal edilmis oturumun access token'i kabul edilmez. */
	@Transactional(readOnly = true)
	public boolean isActive(UUID sessionId, long userId) {
		return sessions.existsByIdAndUserIdAndRevokedAtIsNull(sessionId, userId);
	}

	private Tokens tokens(AuthSession session, Instant now) {
		String refreshToken = randomToken();
		refreshTokens.save(new RefreshToken(session.getId(), sha256(refreshToken),
			now.plus(properties.refreshTokenTtl()), now));
		return new Tokens(accessToken(session, now), refreshToken, properties.accessTokenTtl().toSeconds());
	}

	private String accessToken(AuthSession session, Instant now) {
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(String.valueOf(session.getUserId()))
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenTtl()))
			.id(UUID.randomUUID().toString())
			.claim(SESSION_CLAIM, session.getId().toString())
			.build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private String randomToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
