package com.olivamore.api.integration;

import com.olivamore.api.integration.support.FakeParasut;
import com.olivamore.api.integration.support.FakeTrendyol;
import com.olivamore.api.integration.support.FakeTrendyol.Line;
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
 * Faturanin pazar yerine bildirimi (docs/INVOICES.md), sahte Trendyol ve Parasut ile: resmilestiren Parasut
 * baglantisi varken fatura resmilesince Trendyol paketi resmi numarayla "Invoiced" olur ve resmi belgenin PDF'i
 * yuklenir; yokken bizdeki numarayla, dosyasiz. Hazirlaniyor bildirilmemis siparisin faturasi bildirilmez.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationInvoiceReportIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	String barcode4;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		jdbc.update("update finished_products set total_stock = 500 where id = 4");
		barcode4 = jdbc.queryForObject("select barcode from finished_products where id = 4", String.class);
	}

	@Test
	void formalizedInvoiceIsReportedWithItsOfficialNumberAndFile() {
		long now = Instant.now().toEpochMilli();
		try (FakeTrendyol shop = new FakeTrendyol().product(barcode4, "OLV-0004", "Olivamore Gemlik Sızma 1 L", 30)
			.shipmentPackage(9201, "10656611111", "Created", now, now, "Selin", "Toprak", "selin@example.com",
				"5321230011", "İstanbul", "Beşiktaş", "Barbaros Blv. 10",
				List.of(new Line(91, barcode4, "Olivamore Gemlik Sızma 1 L", 2)));
			FakeParasut parasut = new FakeParasut()) {
			long trendyol = trendyol(shop, "Trendyol fatura");
			long order = confirmedOrder(trendyol, "9201");
			assertThat(shop.statusOf(9201)).isEqualTo("Picking");
			long accounting = parasut(parasut);

			JsonNode invoice = issue(order);

			assertThat(shop.statusOf(9201)).isEqualTo("Invoiced");
			String official = shop.invoiceNumberOf(9201);
			assertThat(official).as("resmi e-arsiv numarasi").startsWith("EAR2026")
				.isNotEqualTo(invoice.get("number").asString());
			assertThat(shop.invoiceFileOf(9201)).isEqualTo(official);
			assertThat(shop.requests()).anyMatch(r -> r.path().endsWith("/seller-invoice-file")
				&& r.body().contains("%PDF") && r.headers().get("content-type").startsWith("multipart/form-data"));
			JsonNode reported = integrationOrder(trendyol, "9201");
			assertThat(reported.get("platform_status").asString()).isEqualTo("invoice_sent");
			assertThat(reported.get("platform_error").isNull()).isTrue();
			assertThat(jdbc.queryForObject("select pdf is not null from integration_invoices where invoice_id = ?",
				Boolean.class, invoice.get("id").asLong())).as("PDF saklanir").isTrue();

			// Bildirilmis fatura bir daha gitmez
			assertThat(data(api.post(base(trendyol) + "/orders/report", Map.of())).get("reported").asInt()).isZero();
			pause(accounting);
			pause(trendyol);
		}
	}

	@Test
	void withoutFormalizationOurNumberIsReportedWithoutAFile() {
		long now = Instant.now().toEpochMilli();
		try (FakeTrendyol shop = new FakeTrendyol().product(barcode4, "OLV-0004", "Olivamore Gemlik Sızma 1 L", 30)
			.shipmentPackage(9301, "10657711111", "Created", now, now, "Kaan", "Demir", "kaan@example.com",
				"5321230021", "Ankara", "Çankaya", "Tunalı Cd. 3",
				List.of(new Line(95, barcode4, "Olivamore Gemlik Sızma 1 L", 1)))) {
			long trendyol = trendyol(shop, "Trendyol numara");
			long order = confirmedOrder(trendyol, "9301");

			JsonNode invoice = issue(order);

			assertThat(shop.statusOf(9301)).isEqualTo("Invoiced");
			assertThat(shop.invoiceNumberOf(9301)).isEqualTo(invoice.get("number").asString());
			assertThat(shop.invoiceFileOf(9301)).isNull();
			assertThat(shop.requests()).noneMatch(r -> r.path().endsWith("/seller-invoice-file"));
			assertThat(integrationOrder(trendyol, "9301").get("platform_status").asString()).isEqualTo("invoiced");
			pause(trendyol);
		}
	}

	private long trendyol(FakeTrendyol shop, String name) {
		long connection = data(api.post("/api/integrations/connections", Map.of("provider", "trendyol", "name", name,
			"base_url", shop.url(), "credentials", Map.of("seller_id", FakeTrendyol.SELLER, "api_key",
				FakeTrendyol.KEY, "api_secret", FakeTrendyol.SECRET)))).get("id").asLong();
		api.post(base(connection) + "/catalog/refresh", Map.of());
		return connection;
	}

	private long parasut(FakeParasut parasut) {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "parasut", "name",
			"Paraşüt Trendyol", "base_url", parasut.url(), "invoice_push_enabled", true, "e_document_enabled", true,
			"credentials", Map.of("company_id", FakeParasut.COMPANY, "client_id", FakeParasut.CLIENT_ID,
				"client_secret", FakeParasut.CLIENT_SECRET, "username", FakeParasut.USERNAME, "password",
				FakeParasut.PASSWORD)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		return data(created).get("id").asLong();
	}

	/** Siparis alinir ve onaylanir (paket hazirlaniyor bildirilir). */
	private long confirmedOrder(long connection, String packageId) {
		assertThat(data(api.post(base(connection) + "/orders/pull", Map.of())).get("imported").asInt())
			.isEqualTo(1);
		long order = integrationOrder(connection, packageId).get("order_id").asLong();
		Response confirmed = api.patch("/api/orders/" + order + "/status", Map.of("status", "confirmed"));
		assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
		assertThat(integrationOrder(connection, packageId).get("platform_status").asString()).isEqualTo("preparing");
		return order;
	}

	private JsonNode issue(long order) {
		Response issued = api.post("/api/orders/" + order + "/invoice", Map.of());
		assertThat(issued.status()).as(issued.body()).isEqualTo(201);
		return data(issued);
	}

	private JsonNode integrationOrder(long connection, String externalId) {
		return StreamSupport.stream(data(api.get(base(connection) + "/orders")).spliterator(), false)
			.filter(o -> o.get("external_id").asString().equals(externalId)).findFirst().orElseThrow();
	}

	private void pause(long connection) {
		api.patch(base(connection), Map.of("status", "paused"));
	}

	private static String base(long connection) {
		return "/api/integrations/connections/" + connection;
	}

}
