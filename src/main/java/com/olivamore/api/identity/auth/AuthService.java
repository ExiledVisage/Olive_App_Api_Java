package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.security.IdentityWebConfig;
import com.olivamore.api.identity.user.User;
import com.olivamore.api.identity.user.UserRepository;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Laravel {@code App\Services\Auth\AuthService}. */
@Service
public class AuthService {

	static final String WRONG_CREDENTIALS = "The provided credentials are incorrect.";

	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository users;
	private final TokenService tokens;

	AuthService(UserRepository users, TokenService tokens) {
		this.users = users;
		this.tokens = tokens;
	}

	public record Result(User user, String token) {
	}

	@Transactional
	public Result register(String name, String email, String password) {
		User user = new User();
		user.setName(name);
		user.setEmail(email);
		user.setPassword(hash(password));
		user.setCreatedAt(Timestamps.now());
		user.setUpdatedAt(user.getCreatedAt());
		users.save(user);
		return new Result(user, tokens.issue(user));
	}

	/** Kullanici yoksa da sifre yanlissa da ayni hata (hangisi oldugu sizdirilmaz). */
	@Transactional
	public Result login(String email, String password) {
		User user = users.findFirstByEmail(email).orElse(null);
		if (user == null || !IdentityWebConfig.PASSWORDS.matches(password, user.getPassword())) {
			throw LaravelValidationException.of("email", WRONG_CREDENTIALS);
		}
		return new Result(user, tokens.issue(user));
	}

	@Transactional
	public void logout(long tokenId) {
		tokens.revoke(tokenId);
	}

	/** {@code Hash::make}. PHP gibi 72 bayttan sonrasi yok sayilir; Spring uzun sifrede hata atiyor. */
	public static String hash(String password) {
		byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
		if (bytes.length <= BCRYPT_MAX_BYTES) {
			return IdentityWebConfig.PASSWORDS.encode(password);
		}
		return BCrypt.hashpw(Arrays.copyOf(bytes, BCRYPT_MAX_BYTES), BCrypt.gensalt("$2y", 12));
	}

}
