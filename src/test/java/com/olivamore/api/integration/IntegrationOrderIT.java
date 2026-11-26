package com.olivamore.api.integration;

import com.olivamore.api.integration.support.FakeWooCommerce;
import com.olivamore.api.integration.support.FakeWooCommerce.Line;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Siparis cekme (docs/INTEGRATIONS.md 0), sahte WooCommerce sitesiyle: eslesen siparis Siparisler'e taslak olarak
 * (kaynak ve site numarasiyla) girer, eslesmeyen bekler ve urun baglaninca yeniden denenir; ayni siparis iki kez
 * alinmaz; musteri e-posta ya da telefonla bulunur, yoksa olusur; yalniz hazirlanacak siparisler cekilir.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationOrderIT {

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
		String now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
		String hourAgo = LocalDateTime.now(ZoneOffset.UTC).minusHours(1).truncatedTo(ChronoUnit.SECONDS).toString();
		site = new FakeWooCommerce()
			.product(11, "Gemlik Sızma 1 L", "SITE-11", barcode4, 7)
			.variable(14, "Gemlik Sızma 0.8")
			.variation(14, 141, "250 ml", "", barcode6, 3)
			.variation(14, 142, "1 L", "", null, 0)
			.order(501, "1001", "processing", now, "Ayşe", "Yıldırım", "ayse.yildirim@example.com", "0555 111 22 33",
				"Karşıyaka", "Cemal Gürsel Cd. No:12", List.of(new Line(11, 0, "Gemlik Sızma 1 L", 2),
					new Line(14, 141, "Gemlik Sızma 0.8 - 250 ml", 1)))
			.order(502, "1002", "processing", hourAgo, "Can", "Er", "can@example.com", "0555 999 88 77", "Bornova",
				"Kazım Dirik Mh. 10", List.of(new Line(14, 142, "Gemlik Sızma 0.8 - 1 L", 3)))
			.order(503, "1003", "on-hold", hourAgo, "Ece", "Su", "ece@example.com", "0555 000 00 00", "Buca",
				"Adres", List.of(new Line(11, 0, "Gemlik Sızma 1 L", 1)))
			.order(504, "1004", "processing", hourAgo, "Fatma", "Kaya", "fatma.kaya@gmail.com", "0232 444 88 55",
				"Konak", "Alsancak", List.of(new Line(11, 0, "Gemlik Sızma 1 L", 6)));
	}

	@AfterAll
	void tearDown() {
		site.close();
	}

	@Test
	void matchedOrdersBecomeDraftsAndUnmatchedWaitUntilTheProductIsLinked() {
		long connection = connect();
		api.post("/api/integrations/connections/" + connection + "/catalog/refresh", Map.of());

		JsonNode first = data(api.post(pull(connection), Map.of()));
		assertThat(first.get("received").asInt()).as("on-hold siparis cekilmez").isEqualTo(3);
		assertThat(first.get("imported").asInt()).isEqualTo(2);
		assertThat(first.get("pending").asInt()).isEqualTo(1);
		assertThat(site.requests()).anyMatch(r -> r.path().startsWith("/orders?status=processing")
			&& r.path().contains("modified_after="));

		JsonNode pending = data(api.get(orders(connection, "pending")));
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).get("external_number").asString()).isEqualTo("1002");
		assertThat(pending.get(0).get("problem").asString()).isEqualTo("Eşleşmeyen ürün: Gemlik Sızma 0.8 - 1 L");
		assertThat(pending.get(0).get("customer").asString()).isEqualTo("Can Er");

		// Eslesen siparis Siparisler'de taslak
		long orderId = imported(connection, "1001").get("order_id").asLong();
		JsonNode order = data(api.get("/api/orders/" + orderId));
		assertThat(order.get("status").asString()).isEqualTo("draft");
		assertThat(order.get("source").asString()).isEqualTo("woocommerce");
		assertThat(order.get("external_ref").asString()).isEqualTo("1001");
		assertThat(order.get("note").asString()).isEqualTo("Kapıya bırakın");
		assertThat(order.get("customer").get("name").asString()).isEqualTo("Ayşe Yıldırım");
		assertThat(order.get("customer").get("email").asString()).isEqualTo("ayse.yildirim@example.com");
		assertThat(order.get("delivery_address").get("city").asString()).isEqualTo("Karşıyaka");
		assertThat(order.get("delivery_address").get("full_address").asString())
			.isEqualTo("Cemal Gürsel Cd. No:12, D:3");
		assertThat(order.get("items")).extracting(i -> i.get("finished_product").get("id").asLong() + "x"
			+ i.get("quantity").asInt()).containsExactly("4x2", "6x1");
		assertThat(order.get("status_history").get(0).get("note").asString()).isEqualTo("Site siparişi #1001 alındı.");

		// Telefonu tutan var olan musteri (Fatma Kaya, +90 232 444 88 55) yeniden olusmaz
		long fatma = imported(connection, "1004").get("order_id").asLong();
		assertThat(data(api.get("/api/orders/" + fatma)).get("customer").get("id").asLong()).isEqualTo(2);

		// Ikinci cekme: son dakikalarda degisen 1001 yeniden gelir ama alinmaz
		JsonNode second = data(api.post(pull(connection), Map.of()));
		assertThat(second.get("received").asInt()).isZero();
		assertThat(second.get("skipped").asInt()).isEqualTo(1);
		assertThat(second.get("pending").asInt()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from orders where source = 'woocommerce'", Long.class))
			.isEqualTo(2);

		// Urun baglaninca bekleyen siparis alinir
		long variant = StreamSupport.stream(data(api.get("/api/integrations/connections/" + connection
			+ "/catalog?status=unmatched")).spliterator(), false).findFirst().orElseThrow().get("id").asLong();
		api.put("/api/integrations/catalog-links/" + variant, Map.of("finished_product_id", 8));
		JsonNode retried = data(api.post("/api/integrations/connections/" + connection + "/orders/retry", Map.of()));
		assertThat(retried.get("imported").asInt()).isEqualTo(1);
		assertThat(retried.get("pending").asInt()).isZero();
		long canOrder = imported(connection, "1002").get("order_id").asLong();
		assertThat(data(api.get("/api/orders/" + canOrder)).get("items").get(0).get("quantity").asInt()).isEqualTo(3);

		JsonNode runs = data(api.get("/api/integrations/connections/" + connection + "/runs"));
		assertThat(runs.get(0).get("kind").asString()).isEqualTo("orders");
		assertThat(runs.get(1).get("summary").asString()).isEqualTo("3 yeni sipariş: 2 alındı, 1 inceleme bekliyor");
		assertThat(data(api.get("/api/integrations/connections/" + connection)).get("last_order_sync_at").isNull())
			.isFalse();
	}

	@Test
	void pullIsRefusedWhenDisabledAndManualOrdersStayManual() {
		long connection = connect();
		api.patch("/api/integrations/connections/" + connection, Map.of("order_pull_enabled", false));
		Response refused = api.post(pull(connection), Map.of());
		assertThat(refused.status()).isEqualTo(422);
		assertThat(refused.body()).contains("sipariş çekme kapalı");

		JsonNode manual = data(api.get("/api/orders/1"));
		assertThat(manual.get("source").asString()).isEqualTo("manual");
		assertThat(manual.get("external_ref").isNull()).isTrue();
	}

	private long connect() {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "woocommerce", "name",
			"Site " + System.nanoTime(), "base_url", site.url(), "credentials", Map.of("consumer_key",
				FakeWooCommerce.KEY, "consumer_secret", FakeWooCommerce.SECRET)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		return data(created).get("id").asLong();
	}

	private JsonNode imported(long connection, String number) {
		return StreamSupport.stream(data(api.get(orders(connection, "imported"))).spliterator(), false)
			.filter(o -> number.equals(o.get("external_number").asString())).findFirst().orElseThrow();
	}

	private static String pull(long connection) {
		return "/api/integrations/connections/" + connection + "/orders/pull";
	}

	private static String orders(long connection, String status) {
		return "/api/integrations/connections/" + connection + "/orders?status=" + status;
	}

}
