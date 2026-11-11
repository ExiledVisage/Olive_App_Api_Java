package com.olivamore.api.identity;

import com.olivamore.api.support.ApiIntegrationTest;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import com.olivamore.api.support.ApiClient;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/** JWT access token, refresh token rotasyonu, tekrar kullanim, cikis, anahtar kontrolleri ve govde siniri. */
@ApiIntegrationTest
class AuthTokenIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String UNAUTHENTICATED = "Unauthenticated.";
	private static final String ADMIN = "admin@olivamore.com";

	@LocalServerPort
	int port;

	@Autowired
	RateLimitBuckets buckets;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RSAKey signingKey;

	RestClient http;

	@BeforeEach
	void setUp() {
		http = ApiClient.http(port);
		buckets.clear();
	}

	@Test
	void loginReturnsSignedAccessTokenAndOpaqueRefreshToken() {
		JsonNode login = TestAuth.login(http, ADMIN);

		assertThat(login.get("token_type").asString()).isEqualTo("Bearer");
		assertThat(login.get("expires_in").asInt()).isEqualTo(900);
		assertThat(login.get("user").get("email").asString()).isEqualTo(ADMIN);
		JsonNode header = decode(login.get(TestAuth.ACCESS_TOKEN).asString(), 0);
		JsonNode claims = decode(login.get(TestAuth.ACCESS_TOKEN).asString(), 1);
		assertThat(header.get("alg").asString()).isEqualTo("RS256");
		assertThat(header.get("kid").asString()).isEqualTo(signingKey.getKeyID());
		assertThat(claims.get("sub").asString()).isEqualTo("1");
		assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(900);

		String refresh = login.get(TestAuth.REFRESH_TOKEN).asString();
		assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Long.class, refresh))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = encode(sha256(?::bytea), 'hex')",
			Long.class, refresh)).isOne();
	}

	@Test
	void accessTokenOpensProtectedEndpointsAndMissingTokenIs401() {
		assertThat(me(TestAuth.accessToken(http, ADMIN)).status()).isEqualTo(200);

		Response anonymous = me(null);
		assertThat(anonymous.status()).isEqualTo(401);
		assertThat(detail(anonymous.body())).isEqualTo(UNAUTHENTICATED);
	}

	@Test
	void refreshRotatesTheTokenPair() {
		JsonNode login = TestAuth.login(http, ADMIN);

		Response refreshed = refresh(login.get(TestAuth.REFRESH_TOKEN).asString());

		assertThat(refreshed.status()).as(refreshed.body()).isEqualTo(200);
		JsonNode body = JSON.readTree(refreshed.body());
		assertThat(body.get(TestAuth.REFRESH_TOKEN).asString()).isNotEqualTo(login.get(TestAuth.REFRESH_TOKEN).asString());
		assertThat(me(body.get(TestAuth.ACCESS_TOKEN).asString()).status()).isEqualTo(200);
	}

	@Test
	void reusedRefreshTokenRevokesTheWholeSession() {
		JsonNode login = TestAuth.login(http, ADMIN);
		String stolen = login.get(TestAuth.REFRESH_TOKEN).asString();
		JsonNode rotated = JSON.readTree(refresh(stolen).body());

		Response reused = refresh(stolen);

		assertThat(reused.status()).isEqualTo(401);
		assertThat(detail(reused.body())).isEqualTo(UNAUTHENTICATED);
		assertThat(refresh(rotated.get(TestAuth.REFRESH_TOKEN).asString()).status()).isEqualTo(401);
		assertThat(me(rotated.get(TestAuth.ACCESS_TOKEN).asString()).status()).isEqualTo(401);
		assertThat(me(login.get(TestAuth.ACCESS_TOKEN).asString()).status()).isEqualTo(401);
	}

	@Test
	void concurrentRefreshesWithTheSameTokenSucceedAtMostOnce() throws Exception {
		String token = TestAuth.login(http, ADMIN).get(TestAuth.REFRESH_TOKEN).asString();
		Callable<Integer> call = () -> refresh(token).status();

		List<Future<Integer>> results;
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			results = executor.invokeAll(List.of(call, call, call, call));
		}

		long succeeded = 0;
		for (Future<Integer> result : results) {
			assertThat(result.get()).isIn(200, 401);
			succeeded += result.get() == 200 ? 1 : 0;
		}
		assertThat(succeeded).isLessThanOrEqualTo(1);
	}

	@Test
	void logoutEndsTheSessionImmediately() {
		JsonNode login = TestAuth.login(http, ADMIN);
		String access = login.get(TestAuth.ACCESS_TOKEN).asString();

		assertThat(ApiClient.call(http, "POST", "/api/auth/logout", access, null).status()).isEqualTo(200);

		assertThat(me(access).status()).isEqualTo(401);
		assertThat(refresh(login.get(TestAuth.REFRESH_TOKEN).asString()).status()).isEqualTo(401);
	}

	@Test
	void expiredRefreshTokenIsRejected() {
		String refresh = TestAuth.login(http, ADMIN).get(TestAuth.REFRESH_TOKEN).asString();
		jdbc.update("update refresh_tokens set expires_at = now() - interval '1 second' "
			+ "where token_hash = encode(sha256(?::bytea), 'hex')", refresh);

		assertThat(refresh(refresh).status()).isEqualTo(401);
	}

	@Test
	void missingRefreshTokenIsAValidationError() {
		Response response = ApiClient.call(http, "POST", "/api/auth/refresh", null, JSON.createObjectNode());

		assertThat(response.status()).isEqualTo(422);
		assertThat(response.body()).contains("The refresh token field is required.");
	}

	@Test
	void validlySignedTokenIsAccepted() {
		assertThat(me(token(signingKey, signingKey.getKeyID(), UnaryOperator.identity())).status()).isEqualTo(200);
	}

	@Test
	void expiredAccessTokenIsRejected() {
		Instant past = Instant.now().minusSeconds(3600);
		Response response = me(token(signingKey, signingKey.getKeyID(), claims -> claims.issuedAt(past.minusSeconds(900)).expiresAt(past)));

		assertThat(response.status()).isEqualTo(401);
		assertThat(detail(response.body())).isEqualTo(UNAUTHENTICATED);
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		RSAKey other = new RSAKeyGenerator(2048).keyID(signingKey.getKeyID()).generate();

		assertThat(me(token(other, signingKey.getKeyID(), UnaryOperator.identity())).status()).isEqualTo(401);
	}

	@Test
	void tokenWithUnknownKidIsRejected() {
		RSAKey sameKeyOtherId = new RSAKey.Builder(signingKey).keyID("baska-anahtar").build();

		assertThat(me(token(sameKeyOtherId, "baska-anahtar", UnaryOperator.identity())).status()).isEqualTo(401);
	}

	@Test
	void tokenFromAnotherIssuerOrWithoutSessionIsRejected() {
		assertThat(me(token(signingKey, signingKey.getKeyID(), claims -> claims.issuer("baskasi"))).status()).isEqualTo(401);
		assertThat(me(token(signingKey, signingKey.getKeyID(), claims -> claims.claim("sid", UUID.randomUUID().toString())))
			.status()).isEqualTo(401);
	}

	@Test
	void malformedBearerIs401() {
		assertThat(me("abc").status()).isEqualTo(401);
		assertThat(me("1|yanlis").status()).isEqualTo(401);
	}

	@Test
	void publicEndpointsIgnoreAnInvalidToken() {
		Response login = ApiClient.call(http, "POST", "/api/auth/login", "suresi.dolmus.token",
			JSON.createObjectNode().put("email", ADMIN).put("password", TestAuth.PASSWORD));

		assertThat(login.status()).as(login.body()).isEqualTo(200);
		assertThat(ApiClient.call(http, "GET", "/api/app/version", "suresi.dolmus.token", null).status()).isNotEqualTo(401);
	}

	@Test
	void roleChangesApplyToAnAlreadyIssuedToken() {
		String email = "rol-" + UUID.randomUUID() + "@olivamore.com";
		String access = JSON.readTree(ApiClient.call(http, "POST", "/api/auth/register", null, JSON.createObjectNode()
			.put("name", "Rol").put("email", email).put("password", "gizli-sifre-123")
			.put("password_confirmation", "gizli-sifre-123")).body()).get(TestAuth.ACCESS_TOKEN).asString();
		long id = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
		assertThat(ApiClient.call(http, "GET", "/api/roles", access, null).status()).isEqualTo(403);

		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'admin'", id);
		assertThat(ApiClient.call(http, "GET", "/api/roles", access, null).status()).isEqualTo(200);

		jdbc.update("delete from user_roles where user_id = ?", id);
		assertThat(ApiClient.call(http, "GET", "/api/roles", access, null).status()).isEqualTo(403);
	}

	@Test
	void deletedUsersTokenStopsWorking() {
		String email = "silinen-" + UUID.randomUUID() + "@olivamore.com";
		String access = JSON.readTree(ApiClient.call(http, "POST", "/api/auth/register", null, JSON.createObjectNode()
			.put("name", "Silinen").put("email", email).put("password", "gizli-sifre-123")
			.put("password_confirmation", "gizli-sifre-123")).body()).get(TestAuth.ACCESS_TOKEN).asString();
		assertThat(me(access).status()).isEqualTo(200);

		jdbc.update("delete from users where email = ?", email);

		assertThat(me(access).status()).isEqualTo(401);
	}

	@Test
	void oversizedBodyIsRejectedBeforeAuthentication() throws Exception {
		byte[] body = ("{\"email\":\"" + "a".repeat(3 * 1024 * 1024) + "\"}").getBytes(StandardCharsets.UTF_8);
		HttpClient client = HttpClient.newHttpClient();
		URI login = URI.create("http://localhost:" + port + "/api/auth/login");

		HttpResponse<String> declared = client.send(HttpRequest.newBuilder(login).header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> chunked = client.send(HttpRequest.newBuilder(login).header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))).build(),
			HttpResponse.BodyHandlers.ofString());

		assertThat(declared.statusCode()).isEqualTo(413);
		assertThat(JSON.readTree(declared.body())).isEqualTo(JSON.readTree("""
			{"title":"Content Too Large","status":413,"detail":"Payload Too Large.","instance":"/api/auth/login"}"""));
		assertThat(chunked.statusCode()).isEqualTo(413);
	}

	private Response me(String token) {
		return ApiClient.call(http, "GET", "/api/auth/me", token, null);
	}

	private Response refresh(String token) {
		return ApiClient.call(http, "POST", "/api/auth/refresh", null,
			JSON.createObjectNode().put(TestAuth.REFRESH_TOKEN, token));
	}

	/** Admin'in gercek oturumuna bagli, istenen sekilde degistirilmis bir token. */
	private String token(RSAKey key, String kid, UnaryOperator<JwtClaimsSet.Builder> change) {
		String real = TestAuth.accessToken(http, ADMIN);
		JsonNode claims = decode(real, 1);
		Instant now = Instant.now();
		JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuer(claims.get("iss").asString())
			.subject(claims.get("sub").asString()).issuedAt(now).expiresAt(now.plusSeconds(300))
			.claim("sid", claims.get("sid").asString());
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
		return encoder.encode(JwtEncoderParameters.from(header, change.apply(builder).build())).getTokenValue();
	}

	/** ProblemDetail cevabinin mesaji. */
	private static String detail(String body) {
		return JSON.readTree(body).get("detail").asString();
	}

	private static JsonNode decode(String jwt, int part) {
		return JSON.readTree(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[part]), StandardCharsets.UTF_8));
	}

}
