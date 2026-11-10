package com.olivamore.api.identity.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.olivamore.api.identity.auth.TokenService;
import com.olivamore.api.identity.role.Role;
import com.olivamore.api.identity.user.UserRepository;
import com.olivamore.api.shared.error.ApiMessages;
import com.olivamore.api.shared.error.Problems;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagerFactory;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /api/**}: stateless, Bearer JWT. {@link #PUBLIC_PATHS} disindaki her istek giris ister; rol ve izinler
 * controller'larda {@code @PreAuthorize}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

	private static final String[] PUBLIC_PATHS = {"/api/auth/login", "/api/auth/register", "/api/auth/refresh",
		"/api/app/version", "/api/app/download"};
	private static final int MIN_KEY_BITS = 2048;

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SecurityFilterChain apiSecurity(HttpSecurity http, TokenService tokens, UserRepository users,
		Assignments assignments, ApiMessages messages, JsonMapper json) throws Exception {
		AuthenticationEntryPoint unauthenticated = (request, response, e) -> {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			Problems.write(request, response, Problems.of(HttpStatus.UNAUTHORIZED, messages.text("{error.unauthenticated}")), json);
		};
		AccessDeniedHandler denied = (request, response, e) ->
			Problems.write(request, response, Problems.of(HttpStatus.FORBIDDEN, AccessDeniedAdvice.WRONG_PERMISSIONS), json);
		return http
			.securityMatcher("/api/**")
			.csrf(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(server -> server
				.bearerTokenResolver(ignoringPublicPaths())
				.authenticationEntryPoint(unauthenticated)
				.accessDeniedHandler(denied)
				.jwt(jwt -> jwt.jwtAuthenticationConverter(
					new AuthenticatedUserConverter(tokens, users, assignments))))
			.exceptionHandling(errors -> errors.authenticationEntryPoint(unauthenticated).accessDeniedHandler(denied))
			.build();
	}

	/** Suresi dolmus token tasiyan istemci public endpoint'lere yine ulasabilmeli. */
	private static BearerTokenResolver ignoringPublicPaths() {
		PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
		RequestMatcher open = new OrRequestMatcher(Arrays.stream(PUBLIC_PATHS)
			.<RequestMatcher>map(paths::matcher).toList());
		DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
		return request -> open.matches(request) ? null : bearer.resolve(request);
	}

	/** Admin rolu, tabloda kaydi olmasa da her izne sahiptir. */
	@Bean
	static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
		handler.setAuthorizationManagerFactory(new AuthorizationManagerFactory<>() {
			@Override
			public AuthorizationManager<MethodInvocation> hasAuthority(String authority) {
				return AuthorizationManagers.anyOf(AuthorityAuthorizationManager.hasRole(Role.ADMIN),
					AuthorityAuthorizationManager.hasAuthority(authority));
			}
		});
		return handler;
	}

	/** Anahtar tanimli degilse uygulama baslamaz; yalnizca {@code test} profili gecici anahtar uretir. */
	@Bean
	RSAKey jwtSigningKey(JwtProperties properties, Environment environment) throws Exception {
		if (StringUtils.hasText(properties.privateKey())) {
			RSAKey key = parse(properties.privateKey(), properties.keyId());
			if (key.size() < MIN_KEY_BITS) {
				throw new IllegalStateException("JWT_PRIVATE_KEY en az " + MIN_KEY_BITS + " bit olmali.");
			}
			return key;
		}
		if (environment.acceptsProfiles(Profiles.of("test"))) {
			return new RSAKeyGenerator(MIN_KEY_BITS).keyID(UUID.randomUUID().toString()).generate();
		}
		throw new IllegalStateException("JWT_PRIVATE_KEY tanimli degil. Olusturmak icin: "
			+ "openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048");
	}

	private static RSAKey parse(String pem, String keyId) throws Exception {
		String text = pem.replace("\\n", "\n");
		RSAPrivateKey privateKey = RsaKeyConverters.pkcs8()
			.convert(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
		RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
		RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
			.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
		return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
	}

	@Bean
	JwtEncoder jwtEncoder(RSAKey key) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
	}

	/** Anahtar {@code kid} ile secilir: farkli kid'li token reddedilir. */
	@Bean
	JwtDecoder jwtDecoder(RSAKey key, JwtProperties properties) throws JOSEException {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())))
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

}
