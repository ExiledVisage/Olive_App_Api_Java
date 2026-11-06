package com.olivamore.api.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sayfalama cikisini Laravel'in gercek cevaplariyla karsilastirir.
 * Fixture: src/test/resources/laravel/pagination.json (GET /api/customers, 16 musteri).
 * Istek ayni host/port ile kurulur, boylece link URL'leri de birebir karsilastirilir.
 */
class LaravelPaginatorTest {

	private static final JsonMapper JSON = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	@TestFactory
	Stream<DynamicTest> matchesLaravel() throws Exception {
		JsonNode fixture;
		try (InputStream in = getClass().getResourceAsStream("/laravel/pagination.json")) {
			fixture = JSON.readTree(in);
		}
		List<DynamicTest> tests = new ArrayList<>();
		for (JsonNode c : fixture.get("cases")) {
			String query = c.get("query").asString();
			tests.add(DynamicTest.dynamicTest(query, () -> {
				HttpServletRequest request = normalized(query);
				int perPage = Listing.perPage(request).orElseThrow();
				int page = Listing.currentPage(request);
				List<Object> items = Collections.nCopies(c.get("items").asInt(), new Object());

				ListResponse.Paginated<Object> result =
					LaravelPaginator.paginate(items, c.get("total").asLong(), page, perPage, request);

				// Metin olarak: alan sirasi da Laravel'le ayni olmali
				assertThat(JSON.writeValueAsString(result.links())).isEqualTo(JSON.writeValueAsString(c.get("links")));
				assertThat(JSON.writeValueAsString(result.meta())).isEqualTo(JSON.writeValueAsString(c.get("meta")));
			}));
		}
		return tests.stream();
	}

	/** Laravel'in yakalandigi adresle ayni istek, InputNormalizationFilter'dan gecmis haliyle. */
	private static HttpServletRequest normalized(String query) throws Exception {
		MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/customers");
		raw.setServerName("localhost");
		raw.setServerPort(8001);
		raw.setQueryString(query);
		UriComponentsBuilder.fromUriString("/api/customers?" + query).build().getQueryParams()
			.forEach((name, values) -> values.forEach(v ->
				raw.addParameter(UriUtils.decode(name, StandardCharsets.UTF_8),
					v == null ? "" : UriUtils.decode(v, StandardCharsets.UTF_8))));

		MockFilterChain chain = new MockFilterChain();
		new InputNormalizationFilter().doFilter(raw, new MockHttpServletResponse(), chain);
		return (HttpServletRequest) chain.getRequest();
	}

}
