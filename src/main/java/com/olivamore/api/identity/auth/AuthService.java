package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.security.IdentityWebConfig;
import com.olivamore.api.identity.user.User;
import com.olivamore.api.identity.user.UserRepository;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

	record Result(User user, String token) {
	}

	@Transactional
	Result register(RegisterRequest request) {
		ValidationErrors errors = new ValidationErrors();
		if (users.existsByEmail(request.email())) {
			errors.add("email", LaravelMessages.UNIQUE);
		}
		if (!request.password().equals(request.passwordConfirmation())) {
			errors.add("password", LaravelMessages.CONFIRMED);
		}
		errors.throwIfAny();
		User user = new User();
		user.setName(request.name());
		user.setEmail(request.email());
		user.setPassword(hash(request.password()));
		user.setCreatedAt(Timestamps.now());
		user.setUpdatedAt(user.getCreatedAt());
		users.save(user);
		return new Result(user, tokens.issue(user));
	}

	/** Kullanici yoksa da sifre yanlissa da ayni hata (hangisi oldugu sizdirilmaz). */
	@Transactional
	Result login(LoginRequest request) {
		User user = users.findFirstByEmail(request.email()).orElse(null);
		if (user == null || !IdentityWebConfig.PASSWORDS.matches(request.password(), user.getPassword())) {
			throw LaravelValidationException.of("email", WRONG_CREDENTIALS);
		}
		return new Result(user, tokens.issue(user));
	}

	@Transactional
	void logout(long tokenId) {
		tokens.revoke(tokenId);
	}

	@Transactional(readOnly = true)
	User current(long userId) {
		return users.findById(userId).orElseThrow();
	}

	/** 72 bayttan sonrasi yok sayilir; Spring uzun sifrede hata atiyor. */
	public static String hash(String password) {
		byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
		if (bytes.length <= BCRYPT_MAX_BYTES) {
			return IdentityWebConfig.PASSWORDS.encode(password);
		}
		return BCrypt.hashpw(Arrays.copyOf(bytes, BCRYPT_MAX_BYTES), BCrypt.gensalt("$2y", 12));
	}

}
