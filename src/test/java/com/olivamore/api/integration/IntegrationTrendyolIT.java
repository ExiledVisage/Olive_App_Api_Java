package com.olivamore.api.integration;

import com.olivamore.api.integration.support.FakeTrendyol;
import com.olivamore.api.integration.support.FakeTrendyol.Line;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trendyol baglantisi (docs/INTEGRATIONS.md 2), sahte Trendyol API'siyle: baglanti denemesi (Basic auth,
 * storeFrontCode, User-Agent), onayli urunlerin barkodla eslesmesi (arsivlenmis alinmaz), Created paketlerin son iki
 * haftadan cekilip siparise donmesi (GMT+3 siparis tarihi), stok-fiyat gonderimi ve kuyruk sonucundaki kalem hatasi,
 * siparis onaylaninca paketin "hazirlaniyor" (Picking) bildirilmesi ve reddedilen bildirimin yeniden denenmesi.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTrendyolIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	FakeTrendyol trendyol;

	String barcode4;
	String barcode6;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		jdbc.update("update finished_products set total_stock = 500 where id in (4, 6)");
		barcode4 = jdbc.queryForObject("select barcode from finished_products where id = 4", String.class);
		barcode6 = jdbc.queryForObject("select barcode from finished_products where id = 6", String.class);
		long now = Instant.now().toEpochMilli();
		long twentyDaysAgo = Instant.now().minus(Duration.ofDays(20)).toEpochMilli();
		// 27.11.2026 13:00 Turkiye saati, Trendyol'un yazdigi gibi (GMT+3 duvar saati milisaniyesi)
		long orderDate = LocalDateTime.of(2026, 11, 27, 13, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
		trendyol = new FakeTrendyol()
			.product(barcode4, "OLV-0004", "Olivamore Gemlik Sızma 1 L", 30)
			.product(barcode6, "OLV-0006", "Olivamore Gemlik 0.8 250 ml", 10)
			.product("8680000000999", "SABUN-1", "Zeytinyağlı Sabun", 5)
			.archived("8680000000111", "Eski Ürün")
			.shipmentPackage(9001, "10654411111", "Created", orderDate, now, "Zeynep", "Arslan", "zeynep@example.com",
				"5321112233", "İstanbul", "Sarıyer", "Bahçeköy Mh. Orman Cd. 5", List.of(
					new Line(71, barcode4, "Olivamore Gemlik Sızma 1 L", 2),
					new Line(72, barcode6, "Olivamore Gemlik 0.8 250 ml", 1)))
			.shipmentPackage(9002, "10654422222", "Created", orderDate, now, "Ali", "Kurt", "ali@example.com",
				"5329998877", "Ankara", "Çankaya", "Kızılay",
				List.of(new Line(73, "8680000000999", "Zeytinyağlı Sabun", 1)))
			.shipmentPackage(9003, "10654433333", "Picking", orderDate, now, "Ece", "Su", "ece@example.com",
				"5320000000", "İzmir", "Buca", "Adres",
				List.of(new Line(74, barcode4, "Olivamore Gemlik Sızma 1 L", 1)))
			.shipmentPackage(9004, "10654444444", "Created", orderDate, twentyDaysAgo, "Eski", "Paket",
				"old@example.com", "5321111111", "Bursa", "Nilüfer", "Adres",
				List.of(new Line(75, barcode4, "Olivamore Gemlik Sızma 1 L", 1)))
			.rejectStockFor(barcode6);
	}

	@AfterAll
	void tearDown() {
		trendyol.close();
	}

	@Test
	void trendyolProductsOrdersAndStockFlowThroughTheCommonPipeline() {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "trendyol", "name",
			"Trendyol mağaza", "base_url", trendyol.url(), "credentials", Map.of("seller_id", FakeTrendyol.SELLER,
				"api_key", FakeTrendyol.KEY, "api_secret", FakeTrendyol.SECRET)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		long connection = data(created).get("id").asLong();
		assertThat(data(created).get("credentials").get("seller_id").asString()).isEqualTo(FakeTrendyol.SELLER);
		assertThat(data(created).get("credentials").get("api_secret").asString()).startsWith("••••");

		JsonNode check = data(api.post(base(connection) + "/check", Map.of()));
		assertThat(check.get("ok").asBoolean()).as(check.toString()).isTrue();
		assertThat(check.get("message").asString()).contains("Trendyol'a bağlanıldı");
		FakeTrendyol.Request first = trendyol.requests().getFirst();
		assertThat(first.headers().get("storefrontcode")).isEqualTo("TR");
		assertThat(first.headers().get("user-agent")).isEqualTo(FakeTrendyol.SELLER + " - SelfIntegration");

		// Urunler barkodla eslesir; arsivlenmis alinmaz
		JsonNode catalog = data(api.post(base(connection) + "/catalog/refresh", Map.of()));
		assertThat(catalog.get("total").asInt()).isEqualTo(3);
		assertThat(catalog.get("matched").asInt()).isEqualTo(2);
		assertThat(catalog.get("unmatched").asInt()).isEqualTo(1);

		// Yalniz son iki haftada degisen Created paketler gelir
		JsonNode pulled = data(api.post(base(connection) + "/orders/pull", Map.of()));
		assertThat(pulled.get("received").asInt()).isEqualTo(2);
		assertThat(pulled.get("imported").asInt()).isEqualTo(1);
		assertThat(pulled.get("pending").asInt()).isEqualTo(1);
		assertThat(trendyol.requests()).anyMatch(r -> r.path().contains("/v2/orders?status=Created&startDate="));

		JsonNode imported = StreamSupport.stream(data(api.get(base(connection) + "/orders?status=imported"))
			.spliterator(), false).findFirst().orElseThrow();
		assertThat(imported.get("external_id").asString()).isEqualTo("9001");
		JsonNode order = data(api.get("/api/orders/" + imported.get("order_id").asLong()));
		assertThat(order.get("source").asString()).isEqualTo("trendyol");
		assertThat(order.get("external_ref").asString()).isEqualTo("10654411111");
		assertThat(order.get("order_date").asString()).startsWith("2026-11-27");
		assertThat(order.get("customer").get("name").asString()).isEqualTo("Zeynep Arslan");
		assertThat(order.get("delivery_address").get("district").asString()).isEqualTo("Sarıyer");
		assertThat(order.get("items")).extracting(i -> i.get("finished_product").get("id").asLong() + "x"
			+ i.get("quantity").asInt()).containsExactly("4x2", "6x1");
		assertThat(order.get("status_history").get(0).get("note").asString())
			.isEqualTo("Trendyol siparişi #10654411111 alındı.");
		JsonNode pending = data(api.get(base(connection) + "/orders?status=pending")).get(0);
		assertThat(pending.get("problem").asString()).isEqualTo("Eşleşmeyen ürün: Zeytinyağlı Sabun");

		// Stok barkodla gider; kuyruk sonucundaki hata kaleme yazilir
		api.patch(base(connection), Map.of("stock_push_enabled", true));
		JsonNode pushed = data(api.post(base(connection) + "/stock/push", Map.of()));
		assertThat(pushed.get("updated").asInt()).isEqualTo(1);
		assertThat(pushed.get("failed").asInt()).isEqualTo(1);
		int sellable4 = StreamSupport.stream(data(api.get(base(connection) + "/stock")).spliterator(), false)
			.filter(l -> l.get("finished_product_id").asLong() == 4).findFirst().orElseThrow().get("sellable").asInt();
		assertThat(trendyol.stockOf(barcode4)).isEqualTo(sellable4);
		assertThat(trendyol.stockOf(barcode6)).as("reddedilen degismez").isEqualTo(10);
		JsonNode failed = StreamSupport.stream(data(api.get(base(connection) + "/stock")).spliterator(), false)
			.filter(l -> l.get("finished_product_id").asLong() == 6).findFirst().orElseThrow();
		assertThat(failed.get("push_error").asString()).isEqualTo("Ürün satışa kapalı.");
		assertThat(trendyol.requests()).anyMatch(r -> r.method().equals("POST")
			&& r.path().endsWith("/products/price-and-inventory") && r.body().contains(barcode4));
	}

	@Test
	void wrongCredentialsAreReportedAndTheLiveAddressIsTheDefault() {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "trendyol", "name",
			"Trendyol yanlış", "base_url", trendyol.url(), "credentials", Map.of("seller_id", FakeTrendyol.SELLER,
				"api_key", "yanlis", "api_secret", "yanlis")));
		long connection = data(created).get("id").asLong();
		JsonNode check = data(api.post(base(connection) + "/check", Map.of()));
		assertThat(check.get("ok").asBoolean()).isFalse();
		assertThat(check.get("message").asString()).contains("401");

		Response live = api.post("/api/integrations/connections", Map.of("provider", "trendyol", "name",
			"Trendyol canlı", "credentials", Map.of("seller_id", "1", "api_key", "k", "api_secret", "s")));
		assertThat(live.status()).as("adres verilmezse canli adres").isEqualTo(201);
		assertThat(data(live).get("base_url").isNull()).isTrue();
	}

	@Test
	void confirmingATrendyolOrderReportsThePackageAsPicking() {
		long now = Instant.now().toEpochMilli();
		try (FakeTrendyol shop = new FakeTrendyol()
			.product(barcode4, "OLV-0004", "Olivamore Gemlik Sızma 1 L", 30)
			.shipmentPackage(9101, "10655511111", "Created", now, now, "Deniz", "Ak", "deniz@example.com", "5321230001",
				"İstanbul", "Kadıköy", "Moda Cd. 1", List.of(new Line(81, barcode4, "Olivamore Gemlik Sızma 1 L", 1)))
			.shipmentPackage(9102, "10655522222", "Created", now, now, "Can", "Er", "can@example.com", "5321230002",
				"İzmir", "Konak", "Alsancak", List.of(new Line(82, barcode4, "Olivamore Gemlik Sızma 1 L", 2)))) {
			long connection = data(api.post("/api/integrations/connections", Map.of("provider", "trendyol", "name",
				"Trendyol bildirim", "base_url", shop.url(), "credentials", Map.of("seller_id", FakeTrendyol.SELLER,
					"api_key", FakeTrendyol.KEY, "api_secret", FakeTrendyol.SECRET)))).get("id").asLong();
			api.post(base(connection) + "/catalog/refresh", Map.of());
			assertThat(data(api.post(base(connection) + "/orders/pull", Map.of())).get("imported").asInt())
				.isEqualTo(2);
			long order1 = orderOf(connection, "9101");
			long order2 = orderOf(connection, "9102");
			assertThat(shop.requests()).as("taslakta bildirim yok").noneMatch(r -> r.method().equals("PUT"));

			// Onay: paket hazirlaniyor, butun kalemleriyle
			Response confirmed = api.patch("/api/orders/" + order1 + "/status", Map.of("status", "confirmed"));
			assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
			assertThat(shop.statusOf(9101)).isEqualTo("Picking");
			FakeTrendyol.Request put = shop.requests().stream().filter(r -> r.method().equals("PUT")).findFirst()
				.orElseThrow();
			assertThat(put.path()).endsWith("/order/sellers/" + FakeTrendyol.SELLER + "/shipment-packages/9101");
			assertThat(put.body()).contains("\"status\":\"Picking\"").contains("\"lineId\":81");
			JsonNode reported = integrationOrder(connection, "9101");
			assertThat(reported.get("platform_status").asString()).isEqualTo("preparing");
			assertThat(reported.get("platform_error").isNull()).isTrue();

			// Musteri Trendyol'da iptal etmis: bizde onay gecer, hata kayda yazilir
			shop.cancel(9102);
			assertThat(api.patch("/api/orders/" + order2 + "/status", Map.of("status", "confirmed")).status())
				.isEqualTo(200);
			JsonNode rejected = integrationOrder(connection, "9102");
			assertThat(rejected.get("platform_status").isNull()).isTrue();
			assertThat(rejected.get("platform_error").asString()).contains("Trendyol hata döndü (400)")
				.contains("Cancelled durumunda");
			assertThat(data(api.get("/api/orders/" + order2)).get("status").asString()).isEqualTo("confirmed");

			// Elle yeniden bildir; bildirilmis siparis bir daha gitmez
			shop.restore(9102);
			assertThat(data(api.post(base(connection) + "/orders/report", Map.of())).get("reported").asInt())
				.isEqualTo(1);
			assertThat(shop.statusOf(9102)).isEqualTo("Picking");
			assertThat(integrationOrder(connection, "9102").get("platform_status").asString()).isEqualTo("preparing");
			assertThat(data(api.post(base(connection) + "/orders/report", Map.of())).get("reported").asInt())
				.isZero();
			assertThat(shop.requests().stream().filter(r -> r.method().equals("PUT")).count()).isEqualTo(3);
		}
	}

	private JsonNode integrationOrder(long connection, String externalId) {
		return StreamSupport.stream(data(api.get(base(connection) + "/orders")).spliterator(), false)
			.filter(o -> o.get("external_id").asString().equals(externalId)).findFirst().orElseThrow();
	}

	private long orderOf(long connection, String externalId) {
		return integrationOrder(connection, externalId).get("order_id").asLong();
	}

	private static String base(long connection) {
		return "/api/integrations/connections/" + connection;
	}

}
