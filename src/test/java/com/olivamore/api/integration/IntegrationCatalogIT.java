package com.olivamore.api.integration;

import com.olivamore.api.integration.support.FakeWooCommerce;
import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Urun eslestirme (docs/INTEGRATIONS.md 0), sahte WooCommerce sitesiyle: cekirdek alandaki ve eklenti alanindaki
 * barkod, buyuk/kucuk harf duyarsiz SKU, varyantli urun, elle baglama ve "eslestirilmesin", yenilemede korunmalari,
 * siteden kalkan urun, senkron gecmisi ve hata.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationCatalogIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	FakeWooCommerce site;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		String barcode4 = jdbc.queryForObject("select barcode from finished_products where id = 4", String.class);
		String barcode6 = jdbc.queryForObject("select barcode from finished_products where id = 6", String.class);
		site = new FakeWooCommerce()
			.product(11, "Gemlik Sızma 1 L", "SITE-11", barcode4, 7)
			.product(12, "Gemlik Sızma 500 ml", "olv-0002", null, null)
			.legacyProduct(13, "Zeytinli Sabun", "SABUN-1", "8680000000013", 40)
			.variable(14, "Gemlik Sızma 0.8")
			.variation(14, 141, "250 ml", "", barcode6, 3)
			.variation(14, 142, "1 L", "", null, 0);
	}

	@AfterAll
	void tearDown() {
		site.close();
	}

	@Test
	void catalogMatchesByBarcodeThenSkuAndKeepsManualDecisions() {
		long connection = connect();

		JsonNode first = data(api.post(refresh(connection), Map.of()));
		assertThat(first.get("total").asInt()).isEqualTo(5);
		assertThat(first.get("matched").asInt()).isEqualTo(3);
		assertThat(first.get("matched_now").asInt()).isEqualTo(3);
		assertThat(first.get("unmatched").asInt()).isEqualTo(2);

		Map<String, JsonNode> byName = byName(data(api.get(catalog(connection, null))));
		assertThat(byName.get("Gemlik Sızma 1 L").get("match_source").asString()).isEqualTo("barcode");
		assertThat(byName.get("Gemlik Sızma 1 L").get("product").get("id").asLong()).isEqualTo(4);
		assertThat(byName.get("Gemlik Sızma 1 L").get("external_stock").asInt()).isEqualTo(7);
		assertThat(byName.get("Gemlik Sızma 500 ml").get("match_source").asString()).isEqualTo("sku");
		assertThat(byName.get("Gemlik Sızma 500 ml").get("product").get("id").asLong()).isEqualTo(2);
		assertThat(byName.get("Gemlik Sızma 0.8 - 250 ml").get("product").get("id").asLong()).isEqualTo(6);
		assertThat(byName.get("Gemlik Sızma 0.8 - 250 ml").get("external_variant_id").asString()).isEqualTo("141");
		assertThat(byName.get("Zeytinli Sabun").get("external_barcode").asString()).isEqualTo("8680000000013");

		JsonNode unmatched = data(api.get(catalog(connection, "unmatched")));
		assertThat(unmatched).extracting(l -> l.get("external_name").asString())
			.containsExactly("Gemlik Sızma 0.8 - 1 L", "Zeytinli Sabun");

		// Varyanti elle bagla, sabunu "eslestirilmesin"
		long variant = byName.get("Gemlik Sızma 0.8 - 1 L").get("id").asLong();
		JsonNode manual = data(api.put("/api/integrations/catalog-links/" + variant, Map.of("finished_product_id", 8)));
		assertThat(manual.get("status").asString()).isEqualTo("matched");
		assertThat(manual.get("match_source").asString()).isEqualTo("manual");
		long soap = byName.get("Zeytinli Sabun").get("id").asLong();
		Map<String, Object> none = new HashMap<>();
		none.put("finished_product_id", null);
		assertThat(data(api.put("/api/integrations/catalog-links/" + soap, none)).get("status").asString())
			.isEqualTo("ignored");

		JsonNode second = data(api.post(refresh(connection), Map.of()));
		assertThat(second.get("matched").asInt()).isEqualTo(4);
		assertThat(second.get("matched_now").asInt()).isZero();
		assertThat(second.get("unmatched").asInt()).as("ignored sayilir ama eslesmedi").isEqualTo(1);
		assertThat(data(api.get(catalog(connection, "ignored")))).hasSize(1);

		// Siteden kalkan urun katalogdan duser; kendi urunumuz "sitede yok" listesine gecer
		site.remove(12);
		JsonNode third = data(api.post(refresh(connection), Map.of()));
		assertThat(third.get("removed").asInt()).isEqualTo(1);
		JsonNode unlinked = data(api.get("/api/integrations/connections/" + connection + "/catalog/unlinked-products"));
		assertThat(unlinked).extracting(p -> p.get("id").asLong()).contains(2L, 1L).doesNotContain(4L, 6L, 8L);

		// "Eslestirilmesin" kaldirilinca satir yeniden bagsiz
		assertThat(data(api.delete("/api/integrations/catalog-links/" + soap)).get("status").asString())
			.isEqualTo("unmatched");
		assertThat(api.put("/api/integrations/catalog-links/" + soap, Map.of("finished_product_id", 999999)).status())
			.isEqualTo(422);

		JsonNode runs = data(api.get("/api/integrations/connections/" + connection + "/runs"));
		assertThat(runs).hasSize(3);
		assertThat(runs.get(0).get("kind").asString()).isEqualTo("catalog");
		assertThat(runs.get(0).get("ok").asBoolean()).isTrue();
		assertThat(runs.get(2).get("summary").asString()).isEqualTo("5 ürün, 3 eşleşti (3 yeni), 2 eşleşmedi");
	}

	@Test
	void failedRefreshIsRecordedOnTheConnection() {
		long connection = connect();
		api.patch("/api/integrations/connections/" + connection, Map.of("credentials",
			Map.of("consumer_secret", "cs_wrong_000000000000")));

		Response failed = api.post(refresh(connection), Map.of());
		assertThat(failed.status()).isEqualTo(422);
		assertThat(failed.body()).contains("401");

		JsonNode run = data(api.get("/api/integrations/connections/" + connection + "/runs")).get(0);
		assertThat(run.get("ok").asBoolean()).isFalse();
		assertThat(run.get("error").asString()).contains("401");
		JsonNode view = data(api.get("/api/integrations/connections/" + connection));
		assertThat(view.get("last_error").asString()).contains("401");
		assertThat(view.get("last_error_at").isNull()).isFalse();
	}

	private long connect() {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "woocommerce", "name",
			"Site " + System.nanoTime(), "base_url", site.url(), "credentials", Map.of("consumer_key",
				FakeWooCommerce.KEY, "consumer_secret", FakeWooCommerce.SECRET)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		return data(created).get("id").asLong();
	}

	private static String refresh(long connection) {
		return "/api/integrations/connections/" + connection + "/catalog/refresh";
	}

	private static String catalog(long connection, String status) {
		return "/api/integrations/connections/" + connection + "/catalog" + (status == null ? "" : "?status=" + status);
	}

	private static Map<String, JsonNode> byName(JsonNode links) {
		Map<String, JsonNode> result = new HashMap<>();
		StreamSupport.stream(links.spliterator(), false).forEach(l -> result.put(l.get("external_name").asString(), l));
		return result;
	}

}
