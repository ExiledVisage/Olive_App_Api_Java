package com.olivamore.api.shared.error;

import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.json.JacksonConfig;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import com.olivamore.api.shared.web.Pages;
import com.olivamore.api.shared.web.WebConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API sozlesmesi: RFC 9457 hatalar, sayfalama, tarih ve sayi bicimi (strict JSON esitligi). */
// Yalnizca shared modulu: diger modullerin WebMvcConfigurer'lari (orn. identity) slice'a alinmaz
@WebMvcTest(controllers = ApiExceptionHandlerTest.TestController.class,
	excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.olivamore\\.api\\.(?!shared\\.).*"))
@Import({ApiExceptionHandler.class, ApiMessages.class, JacksonConfig.class, WebConfig.class,
	ApiExceptionHandlerTest.TestController.class})
class ApiExceptionHandlerTest {

	private static final String UNPROCESSABLE = "\"title\":\"Unprocessable Content\",\"status\":422";

	@Autowired
	MockMvc mvc;

	// ---- 422 ---------------------------------------------------------------

	@Test
	void validationErrorsAreProblemDetails() throws Exception {
		postJson("/test/login", "{}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/login\"" + """
				,"detail":"The email field is required. (and 1 more error)",
				 "errors":{"email":["The email field is required."],"password":["The password field is required."]}}
				""", true));
	}

	@Test
	void ruleValuesAreFilledFromTheAnnotation() throws Exception {
		postJson("/test/login", "{\"email\":\"" + "a".repeat(11) + "\",\"password\":\"x\"}")
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/login\"" + """
				,"detail":"The email field must not be greater than 10 characters.",
				 "errors":{"email":["The email field must not be greater than 10 characters."]}}
				""", true));
	}

	@Test
	void blankStringIsMissing() throws Exception {
		postJson("/test/login", "{\"email\":\"   \",\"password\":\"x\"}")
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/login\"" + """
				,"detail":"The email field is required.","errors":{"email":["The email field is required."]}}
				""", true));
	}

	@Test
	void numberIsNotAString() throws Exception {
		postJson("/test/login", "{\"email\":5,\"password\":\"x\"}")
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/login\"" + """
				,"detail":"The email field must be a string.","errors":{"email":["The email field must be a string."]}}
				""", true));
	}

	@Test
	void requiredQueryParameters() throws Exception {
		mvc.perform(get("/test/range"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/range\"" + """
				,"detail":"The from field is required. (and 1 more error)",
				 "errors":{"from":["The from field is required."],"to":["The to field is required."]}}
				""", true));
	}

	@Test
	void snakeCaseQueryParameterBindsAndReportsSnakeName() throws Exception {
		mvc.perform(get("/test/filter?customer_id=abc"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/filter\"" + """
				,"detail":"The customer id field must be an integer.",
				 "errors":{"customer_id":["The customer id field must be an integer."]}}
				""", true));
		mvc.perform(get("/test/filter?customer_id=7"))
			.andExpect(content().json("{\"customer_id\":7}", true));
	}

	@Test
	void serviceErrorsWithVariables() throws Exception {
		mvc.perform(get("/test/service-errors"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/service-errors\"" + """
				,"detail":"The name field must not be greater than 120 characters. (and 2 more errors)",
				 "errors":{"name":["The name field must not be greater than 120 characters."],
				  "items.0.id":["The selected items.0.id is invalid."],
				  "lot_code":["Bu lot kodu zaten kullanılıyor."]}}
				""", true));
	}

	@Test
	void businessRuleHasOnlyDetail() throws Exception {
		mvc.perform(get("/test/business-rule"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/test/business-rule\"" + """
				,"detail":"Bu ürün tipi silinemez; kullanıldığı yerler: 3 ürün."}
				""", true));
	}

	// ---- 404 / 405 / 429 / 500 ---------------------------------------------

	@Test
	void modelNotFound() throws Exception {
		mvc.perform(get("/test/orders/999999"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{"instance":"/test/orders/999999","title":"Not Found","status":404,"detail":"Order 999999 not found."}
				""", true));
	}

	@Test
	void routeNotFound() throws Exception {
		mvc.perform(get("/api/yok-boyle-bir-yer"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{"instance":"/api/yok-boyle-bir-yer","title":"Not Found","status":404,
				 "detail":"The route api/yok-boyle-bir-yer could not be found."}
				""", true));
	}

	@Test
	void methodNotAllowedOnGetRouteListsHead() throws Exception {
		mvc.perform(post("/test/orders/1"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().json("""
				{"instance":"/test/orders/1","title":"Method Not Allowed","status":405,
				 "detail":"The POST method is not supported for route test/orders/1. Supported methods: GET, HEAD."}
				""", true));
		mvc.perform(patch("/test/login"))
			.andExpect(content().json("""
				{"instance":"/test/login","title":"Method Not Allowed","status":405,
				 "detail":"The PATCH method is not supported for route test/login. Supported methods: POST."}
				""", true));
	}

	@Test
	void tooManyAttemptsKeepsHeaders() throws Exception {
		mvc.perform(get("/test/throttled"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "60"))
			.andExpect(content().json("""
				{"instance":"/test/throttled","title":"Too Many Requests","status":429,"detail":"Too Many Attempts."}
				""", true));
	}

	@Test
	void serverErrorHidesDetails() throws Exception {
		mvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("""
				{"instance":"/test/boom","title":"Internal Server Error","status":500,"detail":"Server Error"}
				""", true));
	}

	// ---- sayfalama -----------------------------------------------------------

	@Test
	void withoutPageParametersAllRowsInOnePage() throws Exception {
		mvc.perform(get("/api/numbers"))
			.andExpect(content().json("""
				{"content":[0,1,2,3,4],"page":{"size":5,"number":0,"total_elements":5,"total_pages":1}}
				""", true));
	}

	@Test
	void pagesAreZeroBased() throws Exception {
		mvc.perform(get("/api/numbers?page=1&size=2"))
			.andExpect(content().json("""
				{"content":[2,3],"page":{"size":2,"number":1,"total_elements":5,"total_pages":3}}
				""", true));
	}

	@Test
	void pageSizeAbove200Is422() throws Exception {
		mvc.perform(get("/api/numbers?page=0&size=201"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/api/numbers\"" + """
				,"detail":"The size field must not be greater than 200.",
				 "errors":{"size":["The size field must not be greater than 200."]}}
				""", true));
	}

	@Test
	void invalidPageParametersAre422() throws Exception {
		mvc.perform(get("/api/numbers?page=-1&size=0"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/api/numbers\"" + """
				,"detail":"The page field must be at least 0. (and 1 more error)",
				 "errors":{"page":["The page field must be at least 0."],"size":["The size field must be at least 1."]}}
				""", true));
		mvc.perform(get("/api/numbers?size=abc"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/api/numbers\"" + """
				,"detail":"The size field must be an integer. (and 1 more error)",
				 "errors":{"size":["The size field must be an integer."],"page":["The page field is required when size is present."]}}
				""", true));
	}

	@Test
	void clientSortIsRejected() throws Exception {
		mvc.perform(get("/api/numbers?sort=id,desc"))
			.andExpect(content().json("{" + UNPROCESSABLE + ",\"instance\":\"/api/numbers\"" + """
				,"detail":"The selected sort is invalid.","errors":{"sort":["The selected sort is invalid."]}}
				""", true));
	}

	// ---- JSON / girdi ------------------------------------------------------

	@Test
	void datesAndNumbersAreIso8601AndJsonNumbers() throws Exception {
		mvc.perform(get("/test/values"))
			.andExpect(content().json("""
				{"created_at":"2026-11-06T15:35:14Z","day":"2026-07-15","quantity":12.5000,"large":10000000}
				""", true));
	}

	@Test
	void dateTimeInputIsConvertedToUtc() throws Exception {
		postJson("/test/moment", "{\"at\":\"2026-11-06T18:35:14+03:00\"}")
			.andExpect(content().json("{\"at\":\"2026-11-06T15:35:14Z\"}", true));
		postJson("/test/moment", "{\"at\":\"2026-11-06\"}")
			.andExpect(content().json("{\"at\":\"2026-11-06T00:00:00Z\"}", true));
		postJson("/test/moment", "{\"at\":\"2026-11-06 15:35\"}")
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void queryParametersAreTrimmedAndBlankIsAbsent() throws Exception {
		mvc.perform(get("/test/echo").param("search", " a ").param("status", ""))
			.andExpect(content().json("{\"search\":\"a\",\"status\":\"<yok>\"}", true));
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	// ---- test controller ---------------------------------------------------

	record LoginRequest(
		@NotBlank(message = MessageKeys.REQUIRED) @Size(max = 10, message = MessageKeys.SIZE_MAX) String email,
		@NotBlank(message = MessageKeys.REQUIRED) String password) {
	}

	record Values(LocalDateTime createdAt, LocalDate day, BigDecimal quantity, BigDecimal large) {
	}

	record Moment(@JsonDeserialize(using = IsoDateTimeDeserializer.class) LocalDateTime at) {
	}

	record RangeQuery(
		@NotNull(message = MessageKeys.REQUIRED) String from,
		@NotNull(message = MessageKeys.REQUIRED) String to) {
	}

	/** snake_case query parametresi: @BindParam ile baglanir. */
	record FilterQuery(@BindParam("customer_id") Long customerId) {
	}

	@RestController
	static class TestController {

		@PostMapping("/test/login")
		Map<String, String> login(@Valid @RequestBody LoginRequest request) {
			return Map.of("email", request.email());
		}

		@GetMapping("/api/numbers")
		PagedModel<Integer> numbers(Pageable pageable) {
			List<Integer> all = IntStream.range(0, 5).boxed().toList();
			return Pages.of(pageable, requested -> {
				int from = (int) Math.min(requested.getOffset(), all.size());
				int to = Math.min(from + requested.getPageSize(), all.size());
				return new PageImpl<>(all.subList(from, to), requested, all.size());
			}, () -> all);
		}

		@GetMapping("/test/range")
		String range(@Valid @ModelAttribute RangeQuery query) {
			return query.from() + query.to();
		}

		@GetMapping("/test/filter")
		Map<String, Long> filter(@Valid @ModelAttribute FilterQuery query) {
			return Map.of("customer_id", query.customerId());
		}

		@GetMapping("/test/service-errors")
		String serviceErrors() {
			ValidationErrors errors = new ValidationErrors();
			errors.add("name", MessageKeys.SIZE_MAX, "max", 120);
			errors.exists("items", "id", List.of(5L), List.of());
			errors.add("lot_code", "Bu lot kodu zaten kullanılıyor.");
			errors.throwIfAny();
			return "";
		}

		@GetMapping("/test/orders/{id}")
		String order(@PathVariable String id) {
			throw new ModelNotFoundException("Order", id);
		}

		@GetMapping("/test/business-rule")
		String businessRule() {
			throw new BusinessRuleException("Bu ürün tipi silinemez; kullanıldığı yerler: 3 ürün.");
		}

		@GetMapping("/test/throttled")
		String throttled() {
			throw new HttpStatusException(429, "{error.too-many-attempts}", Map.of("Retry-After", "60"));
		}

		@GetMapping("/test/boom")
		String boom() {
			throw new IllegalStateException("beklenmeyen: select * from users");
		}

		@GetMapping("/test/values")
		Values values() {
			return new Values(LocalDateTime.parse("2026-11-06T15:35:14"), LocalDate.parse("2026-07-15"),
				new BigDecimal("12.5000"), new BigDecimal("1E+7"));
		}

		@PostMapping("/test/moment")
		Moment moment(@RequestBody Moment moment) {
			return moment;
		}

		@GetMapping("/test/echo")
		Map<String, String> echo(@RequestParam(required = false) String search,
			@RequestParam(required = false) String status) {
			Map<String, String> out = new LinkedHashMap<>();
			out.put("search", search);
			out.put("status", status == null ? "<yok>" : status);
			return out;
		}

	}

}
