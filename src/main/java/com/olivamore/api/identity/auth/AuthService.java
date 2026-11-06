package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.domain.User;
import com.olivamore.api.identity.domain.UserRepository;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Laravel {@code App\Services\Auth\AuthService}. */
@Service
public class AuthService {

	static final String WRONG_CREDENTIALS = "The provided credentials are incorrect.";

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

	/** {@code Hash::make} */
	public static String hash(String password) {
		return IdentityWebConfig.PASSWORDS.encode(password);
	}

}
