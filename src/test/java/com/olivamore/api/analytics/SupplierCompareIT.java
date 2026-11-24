package com.olivamore.api.analytics;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tedarikci karsilastirma (docs/SUPPLIER_COMPARE.md): secilen tedarikciler ve ozellikler yan yana; hacim degerleri
 * analizdeki tedarikci listesiyle ayni; en iyi / en kotu; aylik seri; maliyet yalnizca fiyat yetkisiyle; secim
 * dogrulamasi. Donem demo verinin varis araligi.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupplierCompareIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	String from;

	String to;

	List<Long> suppliers;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		LocalDate last = jdbc.queryForObject("select max(received_at)::date from production_batches", LocalDate.class);
		from = last.minusMonths(6).withDayOfMonth(1).toString();
		to = last.toString();
		suppliers = jdbc.queryForList("""
			select supplier_id from production_batches
			where parent_batch_id is null and supplier_id is not null and received_at >= ?::date
			group by supplier_id order by count(*) desc limit 3
			""", Long.class, from);
	}

	@Test
	void allMetricsSideBySideMatchTheSupplierListAndMarkBestAndWorst() {
		JsonNode result = data(compare("ids=" + ids() + "&from=" + from + "&to=" + to));

		assertThat(result.get("suppliers")).hasSize(3);
		List<String> keys = new ArrayList<>();
		result.get("metrics").forEach(m -> keys.add(m.get("key").asString()));
		assertThat(keys).contains("yield_pct", "olive_kpy", "pomace_kpy", "avg_price_per_kg", "main_channel");
		Map<Long, Double> listed = new HashMap<>();
		data(api.get("/api/analytics/suppliers?from=" + from + "&to=" + to)).get("suppliers")
			.forEach(s -> listed.put(s.get("supplier_id").asLong(), s.get("incoming_kg").asDouble()));
		for (JsonNode supplier : result.get("suppliers")) {
			assertThat(supplier.get("values").get("incoming_kg").asDouble())
				.isEqualTo(listed.get(supplier.get("id").asLong()));
			assertThat(supplier.get("values").get("delivery_count").asInt()).isPositive();
		}
		List<String> marked = new ArrayList<>();
		result.get("suppliers").forEach(s -> s.get("best").forEach(k -> marked.add(k.asString())));
		assertThat(marked).contains("incoming_kg");
		// yonu olmayan ozellik isaretlenmez
		assertThat(marked).doesNotContain("purchase_total", "last_delivery_at");
	}

	@Test
	void selectedMetricsAndMonthlySeries() {
		JsonNode result = data(compare("ids=" + ids() + "&metrics=yield_pct,avg_acidity&series=yield_pct&from=" + from
			+ "&to=" + to));

		assertThat(result.get("metrics")).hasSize(2);
		assertThat(result.get("suppliers").get(0).get("values").propertyNames()).containsExactly("yield_pct",
			"avg_acidity");
		JsonNode series = result.get("series");
		assertThat(series.get("metric").asString()).isEqualTo("yield_pct");
		assertThat(series.get("periods")).hasSize(7);
		assertThat(series.get("lines")).hasSize(3);
		assertThat(series.get("lines").get(0).get("values")).hasSize(7);
	}

	@Test
	void costMetricsNeedThePricePermission() {
		jdbc.update("delete from role_permissions where role_id = 6 and permission_id = 58");
		try {
			Api engineer = api.as("muhendis@olivamore.com");
			JsonNode result = data(engineer.get("/api/analytics/suppliers/compare?ids=" + ids() + "&from=" + from
				+ "&to=" + to));
			Response asked = engineer.get("/api/analytics/suppliers/compare?ids=" + ids()
				+ "&metrics=avg_price_per_kg&from=" + from + "&to=" + to);

			assertThat(result.get("suppliers").get(0).get("values").has("avg_price_per_kg")).isFalse();
			assertThat(result.get("suppliers").get(0).get("values").has("yield_pct")).isTrue();
			assertThat(asked.status()).isEqualTo(422);
		}
		finally {
			jdbc.update("insert into role_permissions (role_id, permission_id) values (6, 58)");
		}
	}

	@Test
	void selectionIsValidated() {
		long first = suppliers.getFirst();
		String range = "&from=" + from + "&to=" + to;

		assertThat(compare("ids=" + first + range).json().get("errors").has("ids")).isTrue();
		assertThat(compare("ids=" + first + ",999999" + range).json().get("errors").has("ids")).isTrue();
		assertThat(compare("ids=" + ids() + "&metrics=yok" + range).json().get("errors").has("metrics")).isTrue();
		assertThat(compare("ids=" + ids() + "&series=main_channel" + range).json().get("errors").has("series"))
			.isTrue();
	}

	private Response compare(String query) {
		return api.get("/api/analytics/suppliers/compare?" + query);
	}

	private String ids() {
		return suppliers.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

}
