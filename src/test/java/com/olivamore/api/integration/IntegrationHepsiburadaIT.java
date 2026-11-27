package com.olivamore.api.integration;

import com.olivamore.api.integration.support.FakeHepsiburada;
import com.olivamore.api.integration.support.FakeHepsiburada.Line;
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

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hepsiburada baglantisi (docs/INTEGRATIONS.md 3), sahte Hepsiburada API'siyle: baglanti denemesi (Basic auth,
 * User-Agent), ilanlarin bizim SKU'yla eslesmesi (HB deposundaki ilan alinmaz), siparis kalemlerinin siparis
 * numarasina gore toplanip siparise donmesi (Turkiye saatiyle tarih, ilce), stok yukleme ve sonucundaki kalem hatasi,
 * siparis onaylaninca kalemlerin paketlenmesi ve reddedilen paketlemenin yeniden denenmesi.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationHepsiburadaIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	FakeHepsiburada hepsiburada;

	String sku4;
	String sku6;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		jdbc.update("update finished_products set total_stock = 500, sku = coalesce(sku, 'HB-T-' || id)"
			+ " where id in (4, 6)");
		sku4 = jdbc.queryForObject("select sku from finished_products where id = 4", String.class);
		sku6 = jdbc.queryForObject("select sku from finished_products where id = 6", String.class);
		hepsiburada = new FakeHepsiburada()
			.listing("HBCV00004", sku4, 30)
			.listing("HBCV00006", sku6, 10)
			.listing("HBCV00999", "SABUN-1", 5)
			.fulfilledByHb("HBCV00777", "FBH-1")
			// 27.11.2026 13:00 Turkiye saati, saat dilimi yazilmadan
			.order("4001", "2026-11-27T13:00:00", "Zeynep Arslan", "zeynep@example.com", "5321112233", "İstanbul",
				"Sarıyer", "Bahçeköy Mh.", "Orman Cd. 5", List.of(
					new Line("line-a1", "HBCV00004", sku4, "Olivamore Gemlik Sızma 1 L", 2),
					new Line("line-a2", "HBCV00006", sku6, "Olivamore Gemlik 0.8 250 ml", 1)))
			.order("4002", "2026-11-27T14:00:00", "Ali Kurt", "ali@example.com", "5329998877", "Ankara", "Çankaya",
				"Kızılay Mh.", "Atatürk Blv. 1",
				List.of(new Line("line-b1", "HBCV00999", "SABUN-1", "Zeytinyağlı Sabun", 1)))
			.rejectStockFor("HBCV00006");
	}

	@AfterAll
	void tearDown() {
		hepsiburada.close();
	}

	@Test
	void hepsiburadaListingsOrdersAndStockFlowThroughTheCommonPipeline() {
		long connection = connect(hepsiburada, "Hepsiburada mağaza");

		JsonNode check = data(api.post(base(connection) + "/check", Map.of()));
		assertThat(check.get("ok").asBoolean()).as(check.toString()).isTrue();
		assertThat(check.get("message").asString()).contains("Hepsiburada'ya bağlanıldı").contains("4 ilan");
		FakeHepsiburada.Request first = hepsiburada.requests().getFirst();
		assertThat(first.headers().get("user-agent")).isEqualTo(FakeHepsiburada.INTEGRATOR);
		assertThat(first.headers().get("x-correlationid")).isNotBlank();

		// Ilanlar bizim SKU'yla eslesir; Hepsiburada deposundaki ilan alinmaz
		JsonNode catalog = data(api.post(base(connection) + "/catalog/refresh", Map.of()));
		assertThat(catalog.get("total").asInt()).isEqualTo(3);
		assertThat(catalog.get("matched").asInt()).isEqualTo(2);
		assertThat(catalog.get("unmatched").asInt()).isEqualTo(1);

		// Kalemler siparis numarasina gore toplanir
		JsonNode pulled = data(api.post(base(connection) + "/orders/pull", Map.of()));
		assertThat(pulled.get("received").asInt()).isEqualTo(2);
		assertThat(pulled.get("imported").asInt()).isEqualTo(1);
		assertThat(pulled.get("pending").asInt()).isEqualTo(1);
		assertThat(hepsiburada.requests()).anyMatch(r -> r.path()
			.equals("/orders/merchantId/" + FakeHepsiburada.MERCHANT + "?offset=0&limit=100"));

		JsonNode imported = integrationOrder(connection, "4001");
		assertThat(imported.get("status").asString()).isEqualTo("imported");
		JsonNode order = data(api.get("/api/orders/" + imported.get("order_id").asLong()));
		assertThat(order.get("source").asString()).isEqualTo("hepsiburada");
		assertThat(order.get("external_ref").asString()).isEqualTo("4001");
		assertThat(order.get("order_date").asString()).startsWith("2026-11-27");
		assertThat(order.get("customer").get("name").asString()).isEqualTo("Zeynep Arslan");
		assertThat(order.get("delivery_address").get("district").asString()).isEqualTo("Sarıyer");
		assertThat(order.get("items")).extracting(i -> i.get("finished_product").get("id").asLong() + "x"
			+ i.get("quantity").asInt()).containsExactly("4x2", "6x1");
		assertThat(order.get("items").get(0).get("unit_price").decimalValue()).as("HB birim fiyati, KDV dahil")
			.isEqualByComparingTo("249.90");
		assertThat(order.get("status_history").get(0).get("note").asString())
			.isEqualTo("Hepsiburada siparişi #4001 alındı.");
		assertThat(integrationOrder(connection, "4002").get("problem").asString())
			.isEqualTo("Eşleşmeyen ürün: Zeytinyağlı Sabun");

		// Ikinci cekmede ayni siparisler yeniden alinmaz
		assertThat(data(api.post(base(connection) + "/orders/pull", Map.of())).get("received").asInt()).isZero();

		// Stok Hepsiburada SKU'suyla gider; yukleme sonucundaki hata kaleme yazilir
		api.patch(base(connection), Map.of("stock_push_enabled", true));
		JsonNode pushed = data(api.post(base(connection) + "/stock/push", Map.of()));
		assertThat(pushed.get("updated").asInt()).isEqualTo(1);
		assertThat(pushed.get("failed").asInt()).isEqualTo(1);
		int sellable4 = stockLine(connection, 4).get("sellable").asInt();
		assertThat(hepsiburada.stockOf("HBCV00004")).isEqualTo(sellable4);
		assertThat(hepsiburada.stockOf("HBCV00006")).as("reddedilen degismez").isEqualTo(10);
		assertThat(stockLine(connection, 6).get("push_error").asString()).isEqualTo("Ürün satışa kapalı.");
		assertThat(hepsiburada.requests()).anyMatch(r -> r.method().equals("POST")
			&& r.path().endsWith("/listings/merchantid/" + FakeHepsiburada.MERCHANT + "/stock-uploads")
			&& r.body().contains("\"hepsiburadaSku\":\"HBCV00004\""));
	}

	@Test
	void confirmingAHepsiburadaOrderPackagesItsLines() {
		try (FakeHepsiburada shop = new FakeHepsiburada()
			.listing("HBCV00004", sku4, 30)
			.order("5001", "2026-11-27T10:00:00+03:00", "Deniz Ak", "deniz@example.com", "5321230001", "İstanbul",
				"Kadıköy", "Moda Mh.", "Moda Cd. 1",
				List.of(new Line("line-c1", "HBCV00004", sku4, "Olivamore Gemlik Sızma 1 L", 1)))
			.order("5002", "2026-11-27T11:00:00+03:00", "Can Er", "can@example.com", "5321230002", "İzmir", "Konak",
				"Alsancak Mh.", "Kıbrıs Şehitleri Cd. 2",
				List.of(new Line("line-d1", "HBCV00004", sku4, "Olivamore Gemlik Sızma 1 L", 2)))) {
			long connection = connect(shop, "Hepsiburada paketleme");
			api.post(base(connection) + "/catalog/refresh", Map.of());
			assertThat(data(api.post(base(connection) + "/orders/pull", Map.of())).get("imported").asInt())
				.isEqualTo(2);
			long order1 = integrationOrder(connection, "5001").get("order_id").asLong();
			long order2 = integrationOrder(connection, "5002").get("order_id").asLong();

			Response confirmed = api.patch("/api/orders/" + order1 + "/status", Map.of("status", "confirmed"));
			assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
			assertThat(shop.packageOf("5001")).isNotNull();
			assertThat(shop.requests()).anyMatch(r -> r.method().equals("POST")
				&& r.path().equals("/packages/merchantId/" + FakeHepsiburada.MERCHANT)
				&& r.body().contains("\"id\":\"line-c1\""));
			assertThat(integrationOrder(connection, "5001").get("platform_status").asString()).isEqualTo("preparing");

			// Musteri iptal etmis: bizde onay gecer, gerekce kayda yazilir; geri alininca elle yeniden bildirilir
			shop.cancel("5002");
			assertThat(api.patch("/api/orders/" + order2 + "/status", Map.of("status", "confirmed")).status())
				.isEqualTo(200);
			assertThat(integrationOrder(connection, "5002").get("platform_error").asString())
				.contains("Hepsiburada hata döndü (400)").contains("Sipariş iptal edilmiş.");
			shop.restore("5002");
			assertThat(data(api.post(base(connection) + "/orders/report", Map.of())).get("reported").asInt())
				.isEqualTo(1);
			assertThat(shop.packageOf("5002")).isNotNull();
		}
	}

	@Test
	void wrongCredentialsAreReported() {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "hepsiburada", "name",
			"Hepsiburada yanlış", "base_url", hepsiburada.url(), "credentials", Map.of("merchant_id",
				FakeHepsiburada.MERCHANT, "username", FakeHepsiburada.USER, "password", "yanlis", "integrator",
				FakeHepsiburada.INTEGRATOR)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		JsonNode check = data(api.post(base(data(created).get("id").asLong()) + "/check", Map.of()));
		assertThat(check.get("ok").asBoolean()).isFalse();
		assertThat(check.get("message").asString()).contains("401");
		assertThat(data(created).get("credentials").get("password").asString()).startsWith("••••");
	}

	private long connect(FakeHepsiburada shop, String name) {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "hepsiburada", "name", name,
			"base_url", shop.url(), "credentials", Map.of("merchant_id", FakeHepsiburada.MERCHANT, "username",
				FakeHepsiburada.USER, "password", FakeHepsiburada.PASSWORD, "integrator", FakeHepsiburada.INTEGRATOR)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		return data(created).get("id").asLong();
	}

	private JsonNode integrationOrder(long connection, String externalId) {
		return StreamSupport.stream(data(api.get(base(connection) + "/orders")).spliterator(), false)
			.filter(o -> o.get("external_id").asString().equals(externalId)).findFirst().orElseThrow();
	}

	private JsonNode stockLine(long connection, long productId) {
		return StreamSupport.stream(data(api.get(base(connection) + "/stock")).spliterator(), false)
			.filter(l -> l.get("finished_product_id").asLong() == productId).findFirst().orElseThrow();
	}

	private static String base(long connection) {
		return "/api/integrations/connections/" + connection;
	}

}
