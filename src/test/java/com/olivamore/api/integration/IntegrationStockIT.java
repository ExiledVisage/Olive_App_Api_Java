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
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stok gonderimi (docs/INTEGRATIONS.md 0), sahte WooCommerce sitesiyle: satilabilir stok (fabrika stogu eksi acik
 * siparisler), kapaliyken reddedilmesi, yalniz degisenin gonderilmesi (varyant dahil), sitede zaten dogru olanin
 * gonderilmemesi, kalem hatasinin kaydedilip sonraki calismada yeniden denenmesi, yeni siparisle stogun dusmesi.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationStockIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	FakeWooCommerce site;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		jdbc.update("update finished_products set total_stock = 500 where id in (2, 4, 6)");
		String barcode4 = jdbc.queryForObject("select barcode from finished_products where id = 4", String.class);
		String barcode6 = jdbc.queryForObject("select barcode from finished_products where id = 6", String.class);
		site = new FakeWooCommerce()
			.product(11, "Gemlik Sızma 1 L", "SITE-11", barcode4, 7)
			.product(15, "Gemlik Sızma 500 ml", "OLV-0002", null, 4)
			.variable(14, "Gemlik Sızma 0.8")
			.variation(14, 141, "250 ml", "", barcode6, 3)
			.rejectStockFor(15);
	}

	@AfterAll
	void tearDown() {
		site.close();
	}

	@Test
	void onlyChangedSellableStockIsPushedAndFailuresAreRetried() {
		long connection = connect();
		api.post("/api/integrations/connections/" + connection + "/catalog/refresh", Map.of());

		Response closed = api.post(push(connection), Map.of());
		assertThat(closed.status()).as("stok gonderimi baslangicta kapali").isEqualTo(422);
		assertThat(closed.body()).contains("stok gönderimi kapalı");

		Map<Long, JsonNode> preview = byProduct(data(api.get(stock(connection))));
		assertThat(preview).containsOnlyKeys(2L, 4L, 6L);
		JsonNode four = preview.get(4L);
		assertThat(four.get("sellable").asInt()).isEqualTo(500 - four.get("committed").asInt());
		assertThat(four.get("external_stock").asInt()).isEqualTo(7);
		assertThat(four.get("needs_push").asBoolean()).isTrue();
		int sellable4 = four.get("sellable").asInt();
		int sellable6 = preview.get(6L).get("sellable").asInt();

		// Sitede adet zaten dogruysa gonderilmez
		site.setStock(11, sellable4);
		api.post("/api/integrations/connections/" + connection + "/catalog/refresh", Map.of());
		assertThat(byProduct(data(api.get(stock(connection)))).get(4L).get("needs_push").asBoolean()).isFalse();

		api.patch("/api/integrations/connections/" + connection, Map.of("stock_push_enabled", true));
		JsonNode first = data(api.post(push(connection), Map.of()));
		assertThat(first.get("checked").asInt()).isEqualTo(3);
		assertThat(first.get("updated").asInt()).isEqualTo(1);
		assertThat(first.get("failed").asInt()).isEqualTo(1);
		assertThat(first.get("unchanged").asInt()).isEqualTo(1);
		assertThat(site.stockOf(141)).isEqualTo(sellable6);
		assertThat(site.requests()).anyMatch(r -> r.method().equals("POST")
			&& r.path().equals("/products/14/variations/batch"));

		Map<Long, JsonNode> after = byProduct(data(api.get(stock(connection))));
		assertThat(after.get(2L).get("push_error").asString()).isEqualTo("Geçersiz ID.");
		assertThat(after.get(2L).get("needs_push").asBoolean()).isTrue();
		assertThat(after.get(4L).get("pushed_stock").asInt()).as("dogru olan gonderilmeden isaretlenir")
			.isEqualTo(sellable4);
		assertThat(after.get(6L).get("pushed_stock").asInt()).isEqualTo(sellable6);

		// Hatali kalem yeniden denenir, digerleri dokunulmaz
		JsonNode second = data(api.post(push(connection), Map.of()));
		assertThat(second.get("updated").asInt()).isZero();
		assertThat(second.get("failed").asInt()).isEqualTo(1);
		assertThat(second.get("unchanged").asInt()).isEqualTo(2);

		// Yeni siparis satilabilir stogu dusurur; bir sonraki gonderimde siteye gider
		Response order = api.post("/api/orders", Map.of("customer_id", 1, "delivery_address_id", 1, "order_date",
			"2026-11-27T10:00:00", "items", List.of(Map.of("finished_product_id", 6, "quantity", 5))));
		assertThat(order.status()).as(order.body()).isEqualTo(201);
		JsonNode third = data(api.post(push(connection), Map.of()));
		assertThat(third.get("updated").asInt()).isEqualTo(1);
		assertThat(site.stockOf(141)).isEqualTo(sellable6 - 5);

		JsonNode runs = data(api.get("/api/integrations/connections/" + connection + "/runs"));
		assertThat(runs.get(0).get("kind").asString()).isEqualTo("stock");
		assertThat(runs.get(2).get("summary").asString())
			.isEqualTo("3 ürün kontrol edildi: 1 güncellendi, 1 hata, 1 zaten doğru");
		assertThat(data(api.get("/api/integrations/connections/" + connection)).get("last_stock_sync_at").isNull())
			.isFalse();
	}

	private long connect() {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "woocommerce", "name",
			"Site " + System.nanoTime(), "base_url", site.url(), "credentials", Map.of("consumer_key",
				FakeWooCommerce.KEY, "consumer_secret", FakeWooCommerce.SECRET)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		return data(created).get("id").asLong();
	}

	private static String push(long connection) {
		return "/api/integrations/connections/" + connection + "/stock/push";
	}

	private static String stock(long connection) {
		return "/api/integrations/connections/" + connection + "/stock";
	}

	private static Map<Long, JsonNode> byProduct(JsonNode lines) {
		Map<Long, JsonNode> result = new HashMap<>();
		StreamSupport.stream(lines.spliterator(), false)
			.forEach(l -> result.put(l.get("finished_product_id").asLong(), l));
		return result;
	}

}
