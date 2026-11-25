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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Karsilastirma (docs/COMPARE.md): her grubun secenekleri ve karsilastirmasi; tedarikci ve musteri degerleri
 * analizdeki listelerle ayni; en iyi / en kotu; aylik seri; donemler; maliyet ve alim kanali yalnizca fiyat
 * yetkisiyle; secim dogrulamasi. Donem demo verinin varis araligi.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompareIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	LocalDate last;

	String range;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		last = jdbc.queryForObject("select max(received_at)::date from production_batches", LocalDate.class);
		range = "from=" + last.minusMonths(6).withDayOfMonth(1) + "&to=" + last;
	}

	@Test
	void subjectsListEveryGroup() {
		List<String> keys = new ArrayList<>();
		data(api.get("/api/analytics/compare")).forEach(s -> keys.add(s.get("key").asString()));

		assertThat(keys).containsExactly("supplier", "field", "product_type", "delivery", "channel", "line",
			"customer", "finished_product", "tank", "machine", "period");
	}

	@Test
	void suppliersMatchTheSupplierListAndMarkBestAndWorst() {
		String ids = top("supplier", 3);
		JsonNode result = data(compare("supplier", "ids=" + ids));

		assertThat(result.get("items")).hasSize(3);
		Map<String, Double> listed = new HashMap<>();
		data(api.get("/api/analytics/suppliers?" + range)).get("suppliers")
			.forEach(s -> listed.put(s.get("supplier_id").asString(), s.get("incoming_kg").asDouble()));
		for (JsonNode item : result.get("items")) {
			assertThat(item.get("values").get("incoming_kg").asDouble()).isEqualTo(listed.get(item.get("id").asString()));
		}
		List<String> marked = new ArrayList<>();
		result.get("items").forEach(s -> s.get("best").forEach(k -> marked.add(k.asString())));
		assertThat(marked).contains("incoming_kg").doesNotContain("purchase_total", "last_delivery_at");
	}

	@Test
	void everyProductionGroupCompares() {
		for (String subject : List.of("field", "product_type", "delivery", "channel", "line")) {
			JsonNode result = data(compare(subject, "ids=" + top(subject, 2)));

			assertThat(result.get("items")).as(subject).hasSize(2);
			for (JsonNode item : result.get("items")) {
				assertThat(item.get("name").asString()).as(subject).isNotBlank();
				assertThat(item.get("values").get("incoming_kg").asDouble()).as(subject).isPositive();
				assertThat(item.get("values").get("delivery_count").asInt()).as(subject).isPositive();
			}
		}
	}

	@Test
	void customersMatchTheCustomerList() {
		JsonNode result = data(compare("customer", "ids=" + top("customer", 3)));

		Map<String, Double> listed = new HashMap<>();
		data(api.get("/api/analytics/customers?" + range)).get("customers")
			.forEach(c -> listed.put(c.get("customer_id").asString(), c.get("litres").asDouble()));
		for (JsonNode item : result.get("items")) {
			assertThat(item.get("values").get("sold_litres").asDouble()).isEqualTo(listed.get(item.get("id").asString()));
			assertThat(item.get("values").get("order_count").asInt()).isPositive();
			assertThat(item.get("values").get("top_product").asString()).isNotBlank();
		}
	}

	@Test
	void finishedProductsTanksAndMachinesCompare() {
		JsonNode products = data(compare("finished_product", "ids=" + top("finished_product", 2)));
		assertThat(products.get("items").get(0).get("values").get("sold_quantity").asLong()).isPositive();

		String tanks = jdbc.queryForList("""
			select storage_unit_id from storage_movements where moved_at >= ?::date
			group by storage_unit_id order by count(*) desc limit 2
			""", Long.class, last.minusMonths(6).withDayOfMonth(1)).stream().map(String::valueOf)
			.collect(Collectors.joining(","));
		JsonNode tank = data(compare("tank", "ids=" + tanks));
		assertThat(tank.get("items").get(0).get("values").get("movement_count").asInt()).isPositive();
		assertThat(tank.get("items").get(0).get("values").has("fill_pct")).isTrue();
		assertThat(data(api.get("/api/analytics/compare/tank/options?" + range))).hasSizeGreaterThanOrEqualTo(2);

		String machines = jdbc.queryForList("""
			select p.machine_id from machine_runs r join machine_parts p on p.id = r.machine_part_id
			where r.started_at >= ?::date group by p.machine_id order by count(*) desc limit 2
			""", Long.class, last.minusMonths(6).withDayOfMonth(1)).stream().map(String::valueOf)
			.collect(Collectors.joining(","));
		JsonNode machine = data(compare("machine", "ids=" + machines));
		JsonNode values = machine.get("items").get(0).get("values");
		assertThat(values.get("run_count").asInt()).isPositive();
		assertThat(values.get("work_hours").asDouble()).isPositive();
	}

	@Test
	void periodsCompareMonthsSeasonsAndYears() {
		YearMonth month = YearMonth.from(last);
		int seasonStart = last.getMonthValue() >= 9 ? last.getYear() : last.getYear() - 1;
		String season = seasonStart + "/" + String.format("%02d", (seasonStart + 1) % 100);
		Response response = api.get("/api/analytics/compare/period?ids=" + month + "," + month.minusMonths(1) + ","
			+ season);
		JsonNode result = data(response);

		assertThat(result.get("items")).hasSize(3);
		assertThat(result.get("items").get(2).get("name").asString()).isEqualTo(season + " hasadi");
		double monthKg = result.get("items").get(0).get("values").get("incoming_kg").asDouble();
		double seasonKg = result.get("items").get(2).get("values").get("incoming_kg").asDouble();
		assertThat(monthKg).isPositive();
		assertThat(seasonKg).isGreaterThanOrEqualTo(monthKg);
		assertThat(result.get("items").get(2).get("values").has("sold_litres")).isTrue();

		long supplier = Long.parseLong(top("supplier", 1));
		JsonNode scoped = data(api.get("/api/analytics/compare/period?ids=" + month + "," + season + "&supplier_id="
			+ supplier));
		assertThat(scoped.get("items").get(1).get("values").get("incoming_kg").asDouble()).isLessThan(seasonKg);
		assertThat(scoped.get("items").get(1).get("values").get("sold_litres").isNull()).isTrue();
		assertThat(api.get("/api/analytics/compare/period?ids=" + month + "," + season + "&series=yield_pct")
			.status()).isEqualTo(422);
	}

	@Test
	void selectedMetricsAndMonthlySeries() {
		JsonNode result = data(compare("supplier", "ids=" + top("supplier", 3)
			+ "&metrics=yield_pct,avg_acidity&series=yield_pct"));

		assertThat(result.get("metrics")).hasSize(2);
		assertThat(result.get("items").get(0).get("values").propertyNames()).containsExactly("yield_pct",
			"avg_acidity");
		JsonNode series = result.get("series");
		assertThat(series.get("metric").asString()).isEqualTo("yield_pct");
		assertThat(series.get("periods")).hasSize(7);
		assertThat(series.get("lines")).hasSize(3);
		assertThat(series.get("lines").get(0).get("values")).hasSize(7);
	}

	@Test
	void costMetricsAndChannelsNeedThePricePermission() {
		String ids = top("supplier", 2);
		jdbc.update("delete from role_permissions where role_id = 6 and permission_id = 58");
		try {
			Api engineer = api.as("muhendis@olivamore.com");
			JsonNode result = data(engineer.get("/api/analytics/compare/supplier?ids=" + ids + "&" + range));
			List<String> subjects = new ArrayList<>();
			data(engineer.get("/api/analytics/compare")).forEach(s -> subjects.add(s.get("key").asString()));

			assertThat(result.get("items").get(0).get("values").has("avg_price_per_kg")).isFalse();
			assertThat(result.get("items").get(0).get("values").has("yield_pct")).isTrue();
			assertThat(engineer.get("/api/analytics/compare/supplier?ids=" + ids + "&metrics=avg_price_per_kg&"
				+ range).status()).isEqualTo(422);
			assertThat(subjects).doesNotContain("channel");
			assertThat(engineer.get("/api/analytics/compare/channel?ids=farmer,trader&" + range).status())
				.isEqualTo(403);
		}
		finally {
			jdbc.update("insert into role_permissions (role_id, permission_id) values (6, 58)");
		}
	}

	@Test
	void selectionIsValidated() {
		String first = top("supplier", 1);

		assertThat(compare("supplier", "ids=" + first).json().get("errors").has("ids")).isTrue();
		assertThat(compare("supplier", "ids=" + first + ",999999").json().get("errors").has("ids")).isTrue();
		assertThat(compare("supplier", "ids=" + top("supplier", 2) + "&metrics=yok").json().get("errors")
			.has("metrics")).isTrue();
		assertThat(compare("supplier", "ids=" + top("supplier", 2) + "&series=last_delivery_at").json()
			.get("errors").has("series")).isTrue();
		assertThat(compare("channel", "ids=farmer,yok").json().get("errors").has("ids")).isTrue();
		assertThat(compare("period", "ids=2026-13,2026").status()).isEqualTo(422);
		assertThat(compare("yok", "ids=1,2").status()).isEqualTo(404);
	}

	/** Secicideki ilk n kalem (en buyukten). */
	private String top(String subject, int n) {
		JsonNode options = data(api.get("/api/analytics/compare/" + subject + "/options?" + range));
		assertThat(options.size()).as(subject).isGreaterThanOrEqualTo(n);
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ids.add(options.get(i).get("id").asString());
		}
		return String.join(",", ids);
	}

	private Response compare(String subject, String query) {
		return api.get("/api/analytics/compare/" + subject + "?" + query + "&" + range);
	}

}
