package com.olivamore.api.identity.security;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.throttle.Limit;
import com.olivamore.api.shared.web.throttle.NamedLimiter;
import com.olivamore.api.shared.web.throttle.RateLimitInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Sifre kodlayici ve rate limit'ler. */
@Configuration(proxyBeanMethods = false)
public class IdentityWebConfig {

	/** {@code BCRYPT_ROUNDS=12}, {@code $2y$}: Laravel ve Java birbirinin hash'ini dogrular. */
	public static final BCryptPasswordEncoder PASSWORDS =
		new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 12);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Sabit limitler: giris yapmis kullanici varsa onun kimligi anahtar olur. */
	@Bean
	RateLimitInterceptor.UserKey rateLimitUserKey() {
		return request -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			return authentication != null && authentication.getPrincipal() instanceof CurrentUser user
				? String.valueOf(user.id()) : null;
		};
	}

	/**
	 * Tek hesaba dakikada 5, ayni adresten butun hesaplara dakikada 20. E-posta kucuk harfe cevrilip kirpilir:
	 * harf degistirerek sinir asilamaz.
	 */
	@Bean
	NamedLimiter loginLimiter() {
		return new NamedLimiter() {
			@Override
			public String name() {
				return "login";
			}

			@Override
			public List<Limit> limits(HttpServletRequest request) {
				String key = loginEmail(request).trim().toLowerCase(Locale.ROOT);
				String ip = request.getRemoteAddr();
				return List.of(Limit.perMinute(5, "login:login:" + key + "|" + ip),
					Limit.perMinute(20, "login:login-ip:" + ip));
			}
		};
	}

	/** Adres basina dakikada 5. */
	@Bean
	NamedLimiter registerLimiter() {
		return new NamedLimiter() {
			@Override
			public String name() {
				return "register";
			}

			@Override
			public List<Limit> limits(HttpServletRequest request) {
				return List.of(Limit.perMinute(5, "register:" + request.getRemoteAddr()));
			}
		};
	}

	/** Adres basina dakikada 20. */
	@Bean
	NamedLimiter refreshLimiter() {
		return new NamedLimiter() {
			@Override
			public String name() {
				return "refresh";
			}

			@Override
			public List<Limit> limits(HttpServletRequest request) {
				return List.of(Limit.perMinute(20, "refresh:" + request.getRemoteAddr()));
			}
		};
	}

	/** Govde okunamiyorsa ya da e-posta yoksa bos: yine de adres ve bos anahtar uzerinden sinirlanir. */
	private static String loginEmail(HttpServletRequest request) {
		try {
			JsonNode email = JSON.readTree(request.getInputStream()).get("email");
			if (email == null || email.isNull()) {
				return "";
			}
			if (!email.isString()) {
				return email.toString();
			}
			return email.asString();
		}
		catch (IOException | RuntimeException unreadable) {
			return "";
		}
	}

}
