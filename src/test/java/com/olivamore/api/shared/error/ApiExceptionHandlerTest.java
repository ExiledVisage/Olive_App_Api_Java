package com.olivamore.api.shared.error;

import com.olivamore.api.shared.json.JacksonConfig;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.web.WebConfig;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hata cevaplarini Laravel'den yakalanan gercek govdelerle karsilastirir
 * (strict JSON esitligi: fazla ya da eksik alan da hatadir).
 */
@WebMvcTest(controllers = ApiExceptionHandlerTest.TestController.class)
@Import({ApiExceptionHandler.class, JacksonConfig.class, WebConfig.class,
	ApiExceptionHandlerTest.TestController.class})
class ApiExceptionHandlerTest {

	@Autowired
	MockMvc mvc;

	// ---- 422 validation ----------------------------------------------------

	@Test
	void bothFieldsMissing() throws Exception {
		// Laravel: POST /api/auth/login {}
		postJson("/api/auth/login", "{}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"The email field is required. (and 1 more error)",
				 "errors":{"email":["The email field is required."],"password":["The password field is required."]}}
				""", true));
	}

	@Test
	void oneFieldMissing() throws Exception {
		// Laravel: POST /api/auth/login {"email":"a@b.c"}
		postJson("/api/auth/login", "{\"email\":\"a@b.c\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"The password field is required.","errors":{"password":["The password field is required."]}}
				""", true));
	}

	@Test
	void blankStringCountsAsMissing() throws Exception {
		// TrimStrings + ConvertEmptyStringsToNull: "   " -> null -> required
		postJson("/api/auth/login", "{\"email\":\"   \",\"password\":\"x\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"The email field is required.","errors":{"email":["The email field is required."]}}
				""", true));
	}

	@Test
	void wrongJsonType() throws Exception {
		postJson("/api/auth/login", "{\"email\":5,\"password\":\"x\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"The email field must be a string.","errors":{"email":["The email field must be a string."]}}
				""", true));
	}

	@Test
	void perPageEmpty() throws Exception {
		// Laravel: GET /api/customers?per_page=
		mvc.perform(get("/api/customers?per_page="))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"The per page field must be an integer. (and 1 more error)",
				 "errors":{"per_page":["The per page field must be an integer.","The per page field must be at least 1."]}}
				""", true));
	}

	@Test
	void perPageRules() throws Exception {
		expectPerPageError("abc", "The per page field must be an integer.");
		expectPerPageError("1.5", "The per page field must be an integer.");
		expectPerPageError("05", "The per page field must be an integer.");
		expectPerPageError("0", "The per page field must be at least 1.");
		expectPerPageError("201", "The per page field must not be greater than 200.");
	}

	@Test
	void requiredQueryParameters() throws Exception {
		// Laravel: GET /api/analytics/yield (from/to zorunlu) - iki hata birlikte
		mvc.perform(get("/test/range"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"The from field is required. (and 1 more error)",
				 "errors":{"from":["The from field is required."],"to":["The to field is required."]}}
				""", true));
	}

	@Test
	void snakeCaseQueryParameterBindsAndReportsSnakeName() throws Exception {
		mvc.perform(get("/test/filter?customer_id=abc"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"The customer id field must be an integer.","errors":{"customer_id":["The customer id field must be an integer."]}}
				""", true));
		mvc.perform(get("/test/filter?customer_id=7"))
			.andExpect(content().json("{\"customer_id\":7}", true));
	}

	@Test
	void businessRuleWithoutErrorsWritesEmptyObject() throws Exception {
		mvc.perform(get("/test/business-rule"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("""
				{"message":"Bu ürün tipi silinemez; kullanıldığı yerler: 3 ürün.","errors":{}}
				""", true));
	}

	// ---- 404 / 405 / 500 ---------------------------------------------------

	@Test
	void modelNotFound() throws Exception {
		// Laravel: GET /api/orders/999999
		mvc.perform(get("/test/orders/999999"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{"message":"No query results for model [App\\\\Models\\\\Order] 999999"}
				""", true));
	}

	@Test
	void routeNotFound() throws Exception {
		// Laravel: GET /api/yok-boyle-bir-yer
		mvc.perform(get("/api/yok-boyle-bir-yer"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{"message":"The route api/yok-boyle-bir-yer could not be found."}
				""", true));
	}

	@Test
	void methodNotAllowed() throws Exception {
		// Laravel: PATCH /api/auth/login
		mvc.perform(patch("/api/auth/login"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().json("""
				{"message":"The PATCH method is not supported for route api/auth/login. Supported methods: POST."}
				""", true));
	}

	@Test
	void methodNotAllowedOnGetRouteListsHead() throws Exception {
		mvc.perform(post("/test/orders/1"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().json("""
				{"message":"The POST method is not supported for route test/orders/1. Supported methods: GET, HEAD."}
				""", true));
	}

	@Test
	void serverError() throws Exception {
		mvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("{\"message\":\"Server Error\"}", true));
	}

	// ---- JSON / girdi ------------------------------------------------------

	@Test
	void datesUseLaravelFormat() throws Exception {
		mvc.perform(get("/test/dates"))
			.andExpect(content().json("""
				{"created_at":"2026-11-06T15:35:14.000000Z","updated_at":"2026-07-15T00:00:00.123456Z"}
				""", true));
	}

	@Test
	void queryParametersAreNormalized() throws Exception {
		mvc.perform(get("/test/echo").param("search", " a ").param("empty", "").param("b", "1").param("a", "2").param("b", "3")
				.param("password", " gizli "))
			.andExpect(content().string("{\"search\":\"a\",\"b\":\"3\",\"a\":\"2\",\"password\":\" gizli \"}"));
	}

	@Test
	void unpaginatedListHasOnlyData() throws Exception {
		mvc.perform(get("/api/customers"))
			.andExpect(content().json("{\"data\":[1,2,3]}", true));
	}

	private org.springframework.test.web.servlet.ResultActions postJson(String url, String body) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void expectPerPageError(String value, String message) throws Exception {
		mvc.perform(get("/api/customers").param("per_page", value))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().json("{\"message\":\"" + message + "\",\"errors\":{\"per_page\":[\"" + message + "\"]}}", true));
	}

	// ---- test controller ---------------------------------------------------

	record LoginRequest(
		@NotBlank(message = LaravelMessages.REQUIRED) String email,
		@NotBlank(message = LaravelMessages.REQUIRED) String password) {
	}

	record Dates(Instant createdAt, LocalDateTime updatedAt) {
	}

	record RangeQuery(
		@NotNull(message = LaravelMessages.REQUIRED) String from,
		@NotNull(message = LaravelMessages.REQUIRED) String to) {
	}

	/** snake_case query parametresi: @BindParam ile baglanir. */
	record FilterQuery(@BindParam("customer_id") Long customerId) {
	}

	@RestController
	static class TestController {

		@PostMapping("/api/auth/login")
		Map<String, String> login(@Valid @RequestBody LoginRequest request) {
			return Map.of("email", request.email());
		}

		@GetMapping("/api/customers")
		ListResponse<Integer> customers(HttpServletRequest request) {
			List<Integer> all = List.of(1, 2, 3);
			return Listing.of(request,
				pageable -> new org.springframework.data.domain.PageImpl<>(all, pageable, all.size()),
				() -> all, i -> i);
		}

		@GetMapping("/test/range")
		String range(@Valid @ModelAttribute RangeQuery query) {
			return query.from() + query.to();
		}

		@GetMapping("/test/filter")
		Map<String, Long> filter(@Valid @ModelAttribute FilterQuery query) {
			return Map.of("customer_id", query.customerId());
		}

		@GetMapping("/test/orders/{id}")
		String order(@PathVariable String id) {
			throw new ModelNotFoundException("Order", id);
		}

		@GetMapping("/test/business-rule")
		String businessRule() {
			throw new BusinessRuleException("Bu ürün tipi silinemez; kullanıldığı yerler: 3 ürün.");
		}

		@GetMapping("/test/boom")
		String boom() {
			throw new IllegalStateException("beklenmeyen");
		}

		@GetMapping("/test/dates")
		Dates dates() {
			return new Dates(Instant.parse("2026-11-06T15:35:14Z"), LocalDateTime.parse("2026-07-15T00:00:00.123456"));
		}

		@GetMapping("/test/echo")
		Map<String, String> echo(HttpServletRequest request) {
			Map<String, String> out = new LinkedHashMap<>();
			request.getParameterMap().forEach((k, v) -> out.put(k, v[0]));
			return out;
		}

	}

}
