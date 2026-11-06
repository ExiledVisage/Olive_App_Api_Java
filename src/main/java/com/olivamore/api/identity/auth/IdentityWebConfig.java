package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.WebConfig;
import com.olivamore.api.shared.web.throttle.Limit;
import com.olivamore.api.shared.web.throttle.NamedLimiter;
import com.olivamore.api.shared.web.throttle.ThrottleInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Locale;

/** Kimlik dogrulama, rate limit'ler ve {@link CurrentUser} parametresi. */
@Configuration(proxyBeanMethods = false)
class IdentityWebConfig implements WebMvcConfigurer {

	/** Laravel {@code BCRYPT_ROUNDS=12}, {@code $2y$}: Laravel ve Java birbirinin hash'ini dogrular. */
	static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 12);

	private final TokenService tokens;

	IdentityWebConfig(TokenService tokens) {
		this.tokens = tokens;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AccessInterceptor(tokens)).addPathPatterns("/api/**").order(WebConfig.ORDER_AUTHENTICATE);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == CurrentUser.class;
			}

			@Override
			public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
				WebDataBinderFactory binderFactory) {
				return Access.current(request.getNativeRequest(HttpServletRequest.class));
			}
		});
	}

	/** {@code throttle:N,M}: giris yapmis kullanici varsa onun kimligi anahtar olur. */
	@Bean
	ThrottleInterceptor.UserKey throttleUserKey() {
		return request -> {
			CurrentUser user = Access.current(request);
			return user == null ? null : String.valueOf(user.id());
		};
	}

	/**
	 * AppServiceProvider: {@code RateLimiter::for('login')}. Tek hesaba dakikada 5, ayni adresten
	 * butun hesaplara dakikada 20. E-posta kucuk harfe cevrilip kirpilir: harf degistirerek sinir asilamaz.
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
				Object email = RequestInput.of(request).get("email");
				String key = (email == null ? "" : String.valueOf(email)).trim().toLowerCase(Locale.ROOT);
				String ip = request.getRemoteAddr();
				return List.of(Limit.perMinute(5, "login:login:" + key + "|" + ip), Limit.perMinute(20, "login:login-ip:" + ip));
			}
		};
	}

	/** {@code RateLimiter::for('register')}: adres basina dakikada 5. */
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

}
