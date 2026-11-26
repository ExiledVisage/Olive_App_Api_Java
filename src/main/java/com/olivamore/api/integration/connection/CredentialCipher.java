package com.olivamore.api.integration.connection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Baglanti erisim bilgilerinin sifrelenmesi: AES-256-GCM, anahtar {@code olive.integration.secret}'in SHA-256
 * ozeti. Kayit: base64(12 bayt IV + sifreli metin + etiket). Anahtar degisirse kayitli bilgiler cozulemez ve
 * baglantilarin bilgileri yeniden girilir.
 */
@Component
class CredentialCipher {

	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	CredentialCipher(@Value("${olive.integration.secret:}") String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("olive.integration.secret (ya da JWT_PRIVATE_KEY) tanimli olmali");
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
			this.key = new SecretKeySpec(digest, "AES");
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv)
				.put(sealed).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	String decrypt(String stored) {
		try {
			byte[] all = Base64.getDecoder().decode(stored);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Erişim bilgileri çözülemedi; sunucu anahtarı değişmiş olabilir.", e);
		}
	}

}
