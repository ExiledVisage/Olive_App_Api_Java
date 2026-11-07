package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.auth.AccessToken;
import com.olivamore.api.identity.auth.AccessTokenRepository;
import com.olivamore.api.identity.user.User;
import com.olivamore.api.identity.user.UserRepository;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Laravel Sanctum'un kisisel erisim token'lari, birebir:
 * <ul>
 *   <li>uretim ({@code createToken}): 40 rastgele harf/rakam + onlarin crc32b ozeti; tabloda duz token'in
 *       SHA-256'si, istemciye {@code "<id>|<duz token>"}</li>
 *   <li>dogrulama ({@code Guard::__invoke}): {@code Bearer} basligi, {@code id|token} ise id ile bulunur ve
 *       hash sabit surede karsilastirilir, degilse hash ile aranir; sahibi {@code App\Models\User} olmali;
 *       {@code expires_at} gecmemis olmali; her dogrulamada {@code last_used_at} guncellenir</li>
 * </ul>
 * Laravel ve Java ayni tabloyu kullandigi icin iki taraftan alinan token digerinde de gecerlidir.
 */
@Service
public class TokenService {

	static final String TOKEN_NAME = "mobile";
	static final String ABILITIES = "[\"*\"]";
	private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	private final AccessTokenRepository tokens;
	private final UserRepository users;
	private final SecureRandom random = new SecureRandom();

	public TokenService(AccessTokenRepository tokens, UserRepository users) {
		this.tokens = tokens;
		this.users = users;
	}

	/** {@code $user->createToken('mobile')->plainTextToken} */
	@Transactional
	public String issue(User user) {
		String plain = generateTokenString();
		AccessToken token = new AccessToken();
		token.setTokenableType(User.MORPH_TYPE);
		token.setTokenableId(user.getId());
		token.setName(TOKEN_NAME);
		token.setToken(sha256(plain));
		token.setAbilities(ABILITIES);
		token.setCreatedAt(Timestamps.now());
		token.setUpdatedAt(token.getCreatedAt());
		tokens.save(token);
		return token.getId() + "|" + plain;
	}

	/** {@code Request::bearerToken()} + Sanctum Guard. */
	@Transactional
	public Optional<CurrentUser> authenticate(String authorizationHeader) {
		String bearer = bearerToken(authorizationHeader);
		if (bearer == null || !isValidBearerToken(bearer)) {
			return Optional.empty();
		}
		Optional<AccessToken> found = findToken(bearer);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		AccessToken token = found.get();
		if (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(Timestamps.now())) {
			return Optional.empty();
		}
		if (!User.MORPH_TYPE.equals(token.getTokenableType())) {
			return Optional.empty();
		}
		Optional<User> user = users.findById(token.getTokenableId());
		if (user.isEmpty()) {
			return Optional.empty();
		}
		// updateLastUsedAt: forceFill(['last_used_at' => now()])->save() -> updated_at de degisir
		token.setLastUsedAt(Timestamps.now());
		token.setUpdatedAt(token.getLastUsedAt());
		User u = user.get();
		return Optional.of(new CurrentUser(u.getId(), u.getName(), u.getEmail(), token.getId()));
	}

	/** {@code $user->currentAccessToken()->delete()} */
	@Transactional
	public void revoke(long tokenId) {
		tokens.deleteById(tokenId);
	}

	// ---- Sanctum yardimcilari ---------------------------------------------

	/** {@code Request::bearerToken}: son "Bearer " sonrasi, virgulle biterse virgule kadar. */
	static String bearerToken(String header) {
		if (header == null) {
			return null;
		}
		int position = header.lastIndexOf("Bearer ");
		if (position < 0) {
			return null;
		}
		String token = header.substring(position + 7);
		int comma = token.indexOf(',');
		return comma >= 0 ? token.substring(0, comma) : token;
	}

	/** {@code Guard::isValidBearerToken}: {@code id|token} ise id rakamlardan olusmali. */
	static boolean isValidBearerToken(String token) {
		if (token.isEmpty()) {
			return false;
		}
		int bar = token.indexOf('|');
		if (bar < 0) {
			return true;
		}
		String id = token.substring(0, bar);
		return !id.isEmpty() && id.chars().allMatch(c -> c >= '0' && c <= '9') && bar < token.length() - 1;
	}

	/** {@code PersonalAccessToken::findToken} */
	private Optional<AccessToken> findToken(String token) {
		int bar = token.indexOf('|');
		if (bar < 0) {
			return tokens.findFirstByToken(sha256(token));
		}
		long id;
		try {
			id = Long.parseLong(token.substring(0, bar));
		}
		catch (NumberFormatException tooLarge) {
			return Optional.empty();
		}
		String plain = token.substring(bar + 1);
		return tokens.findById(id).filter(t -> MessageDigest.isEqual(
			t.getToken().getBytes(StandardCharsets.US_ASCII), sha256(plain).getBytes(StandardCharsets.US_ASCII)));
	}

	/** {@code generateTokenString}: token_prefix bos + {@code Str::random(40)} + crc32b. */
	String generateTokenString() {
		StringBuilder entropy = new StringBuilder(40);
		for (int i = 0; i < 40; i++) {
			entropy.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
		}
		CRC32 crc = new CRC32();
		crc.update(entropy.toString().getBytes(StandardCharsets.US_ASCII));
		return entropy + String.format("%08x", crc.getValue());
	}

	static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
