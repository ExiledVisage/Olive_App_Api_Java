package com.olivamore.api.attendance;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Iscinin kisa omurlu giris kodu: {@code OLVATT1.<kullanici>.<son gecerlilik>.<rastgele>.<imza>}. Imza sunucunun
 * anahtariyla HMAC-SHA256 (ilk 16 bayt). Kod tabloda tutulmaz; ekran goruntusu suresi dolunca gecersiz, sure
 * icinde tekrar okutulursa {@link Attendance} cift okutma sayar.
 */
@Component
class AttendanceCodes {

	static final String PREFIX = "OLVATT1";

	record Issued(String code, LocalDateTime expiresAt) {
	}

	private final AttendanceSettings settings;
	private final byte[] key;
	private final SecureRandom random = new SecureRandom();

	AttendanceCodes(AttendanceSettings settings) {
		this.settings = settings;
		this.key = keyOf(settings.codeSecret());
	}

	Issued issue(long userId) {
		LocalDateTime expires = Timestamps.now().plus(settings.codeTtl());
		byte[] nonce = new byte[6];
		random.nextBytes(nonce);
		String body = PREFIX + "." + userId + "." + expires.toEpochSecond(ZoneOffset.UTC) + "."
			+ HexFormat.of().formatHex(nonce);
		return new Issued(body + "." + sign(body), expires);
	}

	/** Gecerliyse kullanici id'si; degilse kullaniciya gosterilecek gerekceyle hata. */
	long verify(String code) {
		String[] parts = code == null ? new String[0] : code.trim().split("\\.");
		if (parts.length != 5 || !PREFIX.equals(parts[0])) {
			throw new BusinessRuleException("Bu bir giriş kodu değil.");
		}
		String body = String.join(".", Arrays.copyOf(parts, 4));
		if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.US_ASCII),
			parts[4].getBytes(StandardCharsets.US_ASCII))) {
			throw new BusinessRuleException("Giriş kodu geçersiz.");
		}
		long expires;
		long userId;
		try {
			userId = Long.parseLong(parts[1]);
			expires = Long.parseLong(parts[2]);
		} catch (NumberFormatException e) {
			throw new BusinessRuleException("Giriş kodu geçersiz.");
		}
		if (Timestamps.now().toEpochSecond(ZoneOffset.UTC) > expires) {
			throw new BusinessRuleException("Giriş kodunun süresi dolmuş; uygulamadaki güncel kodu okutun.");
		}
		return userId;
	}

	private String sign(String body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			byte[] full = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(full, 16));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private byte[] keyOf(String secret) {
		if (secret == null || secret.isBlank()) {
			byte[] generated = new byte[32];
			random.nextBytes(generated);
			return generated;
		}
		try {
			return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

}
