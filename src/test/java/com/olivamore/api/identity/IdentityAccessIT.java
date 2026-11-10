package com.olivamore.api.identity;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import com.olivamore.api.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'in AuthThrottleTest'i (6 test) ve PermissionTest'in identity'ye ait kismi.
 * Kendi veritabanini kullanir (veri degistirir).
 */
@Import({TestcontainersConfiguration.class, IdentityAccessIT.ProbeController.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=identity-access")
class IdentityAccessIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	@Autowired
	RateLimitBuckets buckets;

	@Autowired
	JdbcTemplate jdbc;

	RestClient http;

	@BeforeEach
	void setUp() {
		http = RestClient.create("http://localhost:" + port);
		buckets.clear();
	}

	// ---- AuthThrottleTest --------------------------------------------------

	@Test
	void sameAccountIsLimitedToFiveAttempts() {
		for (int i = 1; i <= 5; i++) {
			assertThat(attempt("kullanici@olivamore.com", "yanlis").status).as("deneme " + i).isEqualTo(422);
		}
		Response blocked = attempt("kullanici@olivamore.com", "yanlis");
		assertThat(blocked.status).isEqualTo(429);
		assertThat(blocked.body).isEqualTo("{\"message\":\"Too Many Attempts.\"}");
		assertThat(blocked.headers.getFirst("Retry-After")).isNotNull();
	}

	@Test
	void limitedAccountCannotLogInEvenWithTheRightPassword() {
		for (int i = 0; i < 5; i++) {
			attempt("admin@olivamore.com", "yanlis");
		}
		assertThat(attempt("admin@olivamore.com", "123").status).isEqualTo(429);
	}

	@Test
	void changingCaseDoesNotBypassTheLimit() {
		for (int i = 0; i < 5; i++) {
			attempt("kullanici@olivamore.com", "yanlis");
		}
		assertThat(attempt("KULLANICI@olivamore.com", "yanlis").status).isEqualTo(429);
		assertThat(attempt("  Kullanici@Olivamore.com  ", "yanlis").status).isEqualTo(429);
	}

	@Test
	void limitedAccountDoesNotLockOthers() {
		for (int i = 0; i < 5; i++) {
			attempt("kullanici@olivamore.com", "yanlis");
		}
		assertThat(attempt("baskasi@olivamore.com", "yanlis").status).isEqualTo(422);
	}

	@Test
	void spreadingAttemptsOverAccountsIsAlsoStopped() {
		for (int account = 1; account <= 5; account++) {
			for (int i = 0; i < 4; i++) {
				attempt("hesap" + account + "@olivamore.com", "yanlis");
			}
		}
		assertThat(attempt("yepyeni@olivamore.com", "yanlis").status).isEqualTo(429);
	}

	@Test
	void registerIsLimitedToo() {
		for (int i = 1; i <= 5; i++) {
			Response r = post("/api/auth/register", null, Map.of("name", "Kullanici " + i, "email", "yeni" + i + "@olivamore.com",
				"password", "gizli-sifre-123", "password_confirmation", "gizli-sifre-123"));
			assertThat(r.status).as(r.body).isEqualTo(201);
		}
		assertThat(post("/api/auth/register", null, Map.of("name", "Fazladan", "email", "fazladan@olivamore.com",
			"password", "gizli-sifre-123", "password_confirmation", "gizli-sifre-123")).status).isEqualTo(429);
	}

	// ---- PermissionTest (identity) -----------------------------------------

	@Test
	void adminRoleHasEveryPermission() {
		long permissions = jdbc.queryForObject("select count(*) from permissions", Long.class);
		long adminPermissions = jdbc.queryForObject("select count(*) from role_has_permissions rp join roles r on r.id = rp.role_id"
			+ " where r.name = 'admin'", Long.class);
		assertThat(permissions).isEqualTo(54);
		assertThat(adminPermissions).isEqualTo(permissions);
	}

	@ParameterizedTest
	@CsvSource({"uretim@olivamore.com, production_operator", "depo@olivamore.com, warehouse_operator",
		"muhendis@olivamore.com, food_engineer"})
	void factoryAccountsHaveTheirRole(String email, String role) {
		JsonNode me = JSON.readTree(get("/api/auth/me", login(email)).body);
		assertThat(me.get("data").get("roles").toString()).isEqualTo("[\"" + role + "\"]");
	}

	@Test
	void userWithoutPermissionsIsForbiddenEverywhere() {
		jdbc.update("insert into roles (name, guard_name, created_at, updated_at) values ('yetkisiz', 'web', now(), now())");
		String token = post("/api/auth/register", null, Map.of("name", "Yetkisiz", "email", "yetkisiz@olivamore.com",
			"password", "gizli-sifre-123", "password_confirmation", "gizli-sifre-123")).json().get(TestAuth.ACCESS_TOKEN).asString();
		long id = jdbc.queryForObject("select id from users where email = 'yetkisiz@olivamore.com'", Long.class);
		jdbc.update("insert into model_has_roles (role_id, model_type, model_id) select id, 'App\\Models\\User', ? from roles where name = 'yetkisiz'", id);

		for (String path : List.of("/api/users", "/api/roles", "/api/permissions", "/api/users/1")) {
			Response r = get(path, token);
			assertThat(r.status).as(path).isEqualTo(403);
			assertThat(r.body).isEqualTo("{\"message\":\"User does not have the right roles.\"}");
		}
		Response permission = get("/api/test/needs-customer-view", token);
		assertThat(permission.status).isEqualTo(403);
		assertThat(permission.body).isEqualTo("{\"message\":\"User does not have the right permissions.\"}");
	}

	@Test
	void adminPassesEveryPermissionCheck() {
		// Gate::before: admin'in role_has_permissions kaydi olmasa da gecer
		assertThat(get("/api/test/needs-unknown-permission", login("admin@olivamore.com")).status).isEqualTo(200);
		assertThat(get("/api/test/needs-unknown-permission", login("planner@olivamore.com")).status).isEqualTo(403);
	}

	@Test
	void permissionViaRoleIsEnough() {
		// food_engineer rolu customer.view iznine sahip (PermissionTest: gida muhendisi musteri tarafini gorur)
		assertThat(get("/api/test/needs-customer-view", login("muhendis@olivamore.com")).status).isEqualTo(200);
	}

	@Test
	void unknownRouteIs404OnlyForAuthenticatedRequests() {
		// Giris yapmamis istemci route listesini yoklayamaz
		assertThat(get("/api/yok-boyle-bir-yer", null).status).isEqualTo(401);
		assertThat(get("/api/yok-boyle-bir-yer", login("admin@olivamore.com")).status).isEqualTo(404);
		assertThat(get("/api/users", null).status).isEqualTo(401);
	}

	// ---- yardimcilar -------------------------------------------------------

	/** Izin kontrolunu gercek bir endpoint'te denemek icin (izinli moduller sonraki fazlarda gelecek). */
	@RestController
	static class ProbeController {

		@PreAuthorize("hasAuthority('customer.view')")
		@GetMapping("/api/test/needs-customer-view")
		Map<String, String> customerView() {
			return Map.of("ok", "evet");
		}

		@PreAuthorize("hasAuthority('olmayan.izin')")
		@GetMapping("/api/test/needs-unknown-permission")
		Map<String, String> unknown() {
			return Map.of("ok", "evet");
		}

	}

	private record Response(int status, HttpHeaders headers, String body) {
		JsonNode json() {
			return JSON.readTree(body);
		}
	}

	private Response attempt(String email, String password) {
		return post("/api/auth/login", null, Map.of("email", email, "password", password));
	}

	private String login(String email) {
		Response r = post("/api/auth/login", null, Map.of("email", email, "password", "123"));
		assertThat(r.status).as(r.body).isEqualTo(200);
		return r.json().get(TestAuth.ACCESS_TOKEN).asString();
	}

	private Response get(String path, String token) {
		RestClient.RequestHeadersSpec<?> spec = http.get().uri(path).accept(MediaType.APPLICATION_JSON);
		if (token != null) {
			spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return spec.exchange((req, res) -> new Response(res.getStatusCode().value(), res.getHeaders(),
			new String(res.getBody().readAllBytes())));
	}

	private Response post(String path, String token, Map<String, ?> body) {
		RestClient.RequestBodySpec spec = http.post().uri(path).accept(MediaType.APPLICATION_JSON)
			.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
		if (token != null) {
			spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return spec.exchange((req, res) -> new Response(res.getStatusCode().value(), res.getHeaders(),
			new String(res.getBody().readAllBytes())));
	}

}
