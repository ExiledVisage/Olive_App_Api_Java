package com.olivamore.api.integration;

import com.olivamore.api.integration.support.FakeHepsiburada;
import com.olivamore.api.integration.support.FakeTrendyol;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pazar yeri kargo etiketi ve "tedarik edilemedi" (docs/INTEGRATIONS.md), sahte Trendyol ve Hepsiburada ile:
 * Trendyol'da paketin takip numarasiyla ortak etiket (ZPL), Hepsiburada'da paketleme cevabindaki paket numarasiyla
 * kargo bilgisi ve barkod; hazirlaniyor bildirilmemis ya da pazar yerinden gelmemis sipariste etiket yok; bizde
 * iptal edilen Trendyol siparisinin tedarik edilemedi bildirimi, reddedilince yeniden denenmesi.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationShippingIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	String barcode4;
	String sku4;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		jdbc.update("update finished_products set total_stock = 500, sku = coalesce(sku, 'SHIP-T-4') where id = 4");
		barcode4 = jdbc.queryForObject("select barcode from finished_products where id = 4", String.class);
		sku4 = jdbc.queryForObject("select sku from finished_products where id = 4", String.class);
	}

	@Test
	void trendyolLabelUsesThePackageTrackingNumberAndCancelledOrdersAreUnsupplied() {
		long now = Instant.now().toEpochMilli();
		try (FakeTrendyol shop = new FakeTrendyol().product(barcode4, "OLV-0004", "Olivamore Gemlik Sızma 1 L", 30)
			.shipmentPackage(9401, "10658811111", "Created", now, now, "Mert", "Aydın", "mert@example.com",
				"5321230031", "Bursa", "Nilüfer", "Görükle Mh. 1", List.of(line(97, 1)))
			.shipmentPackage(9402, "10658822222", "Created", now, now, "Ece", "Kaya", "ece@example.com", "5321230032",
				"Bursa", "Osmangazi", "Çekirge Cd. 2", List.of(line(98, 2)))
			.shipmentPackage(9403, "10658833333", "Created", now, now, "Can", "Tan", "can@example.com", "5321230033",
				"Bursa", "Yıldırım", "Emir Sultan 3", List.of(line(99, 1)))) {
			long connection = data(api.post("/api/integrations/connections", Map.of("provider", "trendyol", "name",
				"Trendyol kargo", "base_url", shop.url(), "credentials", Map.of("seller_id", FakeTrendyol.SELLER,
					"api_key", FakeTrendyol.KEY, "api_secret", FakeTrendyol.SECRET)))).get("id").asLong();
			api.post(base(connection) + "/catalog/refresh", Map.of());
			assertThat(data(api.post(base(connection) + "/orders/pull", Map.of())).get("imported").asInt())
				.isEqualTo(3);
			long labelled = orderOf(connection, "9401");

			assertRule(api.get(label(labelled)), "Etiket için sipariş önce onaylanmalı ve Trendyol'a hazırlanıyor "
				+ "bildirilmeli.");
			confirm(labelled);
			JsonNode label = data(api.get(label(labelled)));
			assertThat(label.get("platform").asString()).isEqualTo("Trendyol");
			assertThat(label.get("tracking_number").asString()).isEqualTo("73300009401");
			assertThat(label.get("cargo_company").asString()).isEqualTo("Trendyol Express Marketplace");
			assertThat(label.get("format").asString()).isEqualTo("ZPL");
			assertThat(label.get("labels").get(0).asString()).startsWith("^XA").contains("73300009401");
			assertThat(shop.requests()).anyMatch(r -> r.method().equals("POST")
				&& r.path().endsWith("/sellers/" + FakeTrendyol.SELLER + "/common-label/73300009401")
				&& r.body().contains("\"format\":\"ZPL\""));

			// Bizde iptal: paket tedarik edilemedi (stok tukendi) olur
			long cancelled = orderOf(connection, "9402");
			confirm(cancelled);
			status(cancelled, "cancelled", "Stok yetmedi");
			assertThat(shop.statusOf(9402)).isEqualTo("UnSupplied");
			assertThat(shop.requests()).anyMatch(r -> r.path().endsWith("/shipment-packages/9402/items/unsupplied")
				&& r.body().contains("\"reasonId\":500") && r.body().contains("\"lineId\":98"));
			assertThat(integrationOrder(connection, "9402").get("platform_status").asString()).isEqualTo("unsupplied");

			// Musteri Trendyol'da zaten iptal etmis: reddedilir, geri alininca elle yeniden bildirilir
			long rejected = orderOf(connection, "9403");
			shop.cancel(9403);
			status(rejected, "cancelled", "Müşteri vazgeçti");
			assertThat(integrationOrder(connection, "9403").get("platform_error").asString())
				.contains("tedarik edilemedi bildirilemez");
			shop.restore(9403);
			assertThat(data(api.post(base(connection) + "/orders/report", Map.of())).get("reported").asInt())
				.isEqualTo(1);
			assertThat(shop.statusOf(9403)).isEqualTo("UnSupplied");
			api.patch(base(connection), Map.of("status", "paused"));
		}
	}

	@Test
	void hepsiburadaLabelUsesThePackageNumberFromPackaging() {
		try (FakeHepsiburada shop = new FakeHepsiburada().listing("HBCV00004", sku4, 30)
			.order("6001", "2026-11-27T10:00:00+03:00", "Derya Su", "derya@example.com", "5321230041", "Antalya",
				"Muratpaşa", "Lara Mh.", "Lara Cd. 4",
				List.of(new FakeHepsiburada.Line("line-e1", "HBCV00004", sku4, "Olivamore Gemlik Sızma 1 L", 1)))) {
			long connection = data(api.post("/api/integrations/connections", Map.of("provider", "hepsiburada", "name",
				"Hepsiburada kargo", "base_url", shop.url(), "credentials", Map.of("merchant_id",
					FakeHepsiburada.MERCHANT, "username", FakeHepsiburada.USER, "password", FakeHepsiburada.PASSWORD,
					"integrator", FakeHepsiburada.INTEGRATOR)))).get("id").asLong();
			api.post(base(connection) + "/catalog/refresh", Map.of());
			api.post(base(connection) + "/orders/pull", Map.of());
			long order = orderOf(connection, "6001");
			confirm(order);
			String packageNumber = shop.packageOf("6001");
			assertThat(packageNumber).isNotNull();

			JsonNode label = data(api.get(label(order)));
			assertThat(label.get("platform").asString()).isEqualTo("Hepsiburada");
			assertThat(label.get("cargo_company").asString()).isEqualTo("HepsiJET");
			assertThat(label.get("tracking_number").asString()).isEqualTo("HJ" + packageNumber);
			assertThat(label.get("format").asString()).isEqualTo("ZPL");
			assertThat(label.get("labels").get(0).asString()).contains(packageNumber);
			assertThat(shop.requests()).anyMatch(r -> r.path().equals("/packages/merchantid/"
				+ FakeHepsiburada.MERCHANT + "/packagenumber/" + packageNumber + "/labels?format=ZPL"));
			api.patch(base(connection), Map.of("status", "paused"));
		}
	}

	@Test
	void manualOrdersHaveNoMarketplaceLabel() {
		long address = jdbc.queryForObject("select id from customer_addresses where customer_id = 1 order by id"
			+ " limit 1", Long.class);
		long order = data(api.post("/api/orders", Map.of("customer_id", 1, "delivery_address_id", address,
			"order_date", "2026-11-27", "items", List.of(Map.of("finished_product_id", 4, "quantity", 1)))))
			.get("id").asLong();
		assertRule(api.get(label(order)), "Bu sipariş bir pazar yerinden gelmedi; etiket yok.");
		assertThat(api.as("operator1@olivamore.com").get(label(order)).status()).isEqualTo(403);
	}

	private FakeTrendyol.Line line(long lineId, int quantity) {
		return new FakeTrendyol.Line(lineId, barcode4, "Olivamore Gemlik Sızma 1 L", quantity);
	}

	private void confirm(long order) {
		status(order, "confirmed", null);
	}

	private void status(long order, String status, String note) {
		Map<String, Object> body = note == null ? Map.of("status", status) : Map.of("status", status, "note", note);
		Response r = api.patch("/api/orders/" + order + "/status", body);
		assertThat(r.status()).as(r.body()).isEqualTo(200);
	}

	private long orderOf(long connection, String externalId) {
		return integrationOrder(connection, externalId).get("order_id").asLong();
	}

	private JsonNode integrationOrder(long connection, String externalId) {
		return StreamSupport.stream(data(api.get(base(connection) + "/orders")).spliterator(), false)
			.filter(o -> o.get("external_id").asString().equals(externalId)).findFirst().orElseThrow();
	}

	private static String label(long order) {
		return "/api/integrations/sales-orders/" + order + "/label";
	}

	private static String base(long connection) {
		return "/api/integrations/connections/" + connection;
	}

	private static void assertRule(Response r, String detail) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).isEqualTo(detail);
	}

}
