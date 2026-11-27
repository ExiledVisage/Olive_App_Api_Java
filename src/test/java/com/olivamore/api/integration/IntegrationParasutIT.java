package com.olivamore.api.integration;

import com.olivamore.api.integration.support.FakeParasut;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parasut baglantisi (docs/INVOICES.md), sahte Parasut API'siyle: OAuth2 sifre akisi, kesilen faturanin gonderimi
 * (cari ve urun bulma/acma ve yeniden kullanma, KDV haric birim fiyat), e-fatura mukellefine e-fatura, digerine
 * e-arsiv (pazar yeri siparisinde internet satisi bilgisi), resmi numara, hata ve kaldigi adimdan yeniden deneme,
 * iptal; muhasebe baglantisinda satis kanali islerinin olmamasi.
 * Demo veri: urun 4 (1 L) 600 TL, urun 1 (250 ml) 150 TL; ikisi de %1 KDV.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationParasutIT {

	private static final String COMPANY_VKN = "1234567801";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void issuedInvoicesAreSentToParasutAndFormalized() {
		try (FakeParasut parasut = new FakeParasut().eInvoiceUser(COMPANY_VKN, "urn:mail:defaultpk@zeytinkoy.com")) {
			long connection = connect(parasut, "Paraşüt resmi", true, true);
			JsonNode check = data(api.post(base(connection) + "/check", Map.of()));
			assertThat(check.get("ok").asBoolean()).as(check.toString()).isTrue();
			assertThat(check.get("message").asString()).contains("Paraşüt'e bağlanıldı").contains("0 cari");

			// Firma, e-fatura mukellefi: e-fatura
			long company = customer("Zeytinköy Gıda Ltd.", COMPANY_VKN, "zeytinkoy@example.com");
			JsonNode first = issue(order(company, Map.of(4, 2, 1, 1)));
			JsonNode export = exportOf(first.get("id").asLong(), connection);
			assertThat(export.get("status").asString()).as(export.toString()).isEqualTo("formalized");
			assertThat(export.get("e_document_type").asString()).isEqualTo("e_invoice");
			assertThat(export.get("official_number").asString()).startsWith("EFT2026");
			assertThat(export.get("error").isNull()).isTrue();

			assertThat(parasut.contacts()).hasSize(1);
			Map<String, Object> contact = parasut.contacts().values().iterator().next();
			assertThat(contact).containsEntry("tax_number", COMPANY_VKN).containsEntry("contact_type", "company")
				.containsEntry("account_type", "customer");
			assertThat(parasut.products()).hasSize(2);
			Map<String, Object> invoice = parasut.invoices().values().iterator().next();
			assertThat(attributes(invoice)).containsEntry("description", first.get("number").asString())
				.containsEntry("currency", "TRL").containsEntry("tax_number", COMPANY_VKN);
			// 1 L x 2: 1200 TL KDV dahil, 1188.12 net
			Map<?, ?> detail = ((List<?>) invoice.get("details")).stream()
				.map(d -> (Map<?, ?>) ((Map<?, ?>) d).get("attributes"))
				.filter(a -> Integer.valueOf(2).equals(a.get("quantity"))).findFirst().orElseThrow();
			assertThat(new BigDecimal(detail.get("unit_price").toString())).as("KDV haric birim fiyat")
				.isEqualByComparingTo("594.06");
			assertThat(detail.get("vat_rate")).isEqualTo(1);
			assertThat(parasut.requests()).anyMatch(r -> r.path().endsWith("/e_invoices")
				&& r.body().contains("\"to\":\"urn:mail:defaultpk@zeytinkoy.com\"")
				&& r.body().contains("\"scenario\":\"basic\""));

			// Ayni musterinin ikinci faturasi: cari ve urun yeniden acilmaz
			JsonNode second = issue(order(company, Map.of(4, 1)));
			assertThat(exportOf(second.get("id").asLong(), connection).get("status").asString())
				.isEqualTo("formalized");
			assertThat(parasut.contacts()).hasSize(1);
			assertThat(parasut.products()).hasSize(2);
			assertThat(parasut.requests().stream().filter(r -> r.method().equals("POST")
				&& r.path().endsWith("/contacts"))).hasSize(1);

			// Vergi numarasiz kisi, Trendyol siparisi: e-arsiv, internet satisi bilgisiyle
			long person = customer("Ayşe Yılmaz", null, "ayse@example.com");
			long marketplaceOrder = order(person, Map.of(1, 2));
			jdbc.update("update orders set source = 'trendyol', external_ref = 'TY-9001' where id = ?",
				marketplaceOrder);
			JsonNode archived = issue(marketplaceOrder);
			JsonNode archive = exportOf(archived.get("id").asLong(), connection);
			assertThat(archive.get("e_document_type").asString()).isEqualTo("e_archive");
			assertThat(archive.get("official_number").asString()).startsWith("EAR2026");
			assertThat(parasut.requests()).anyMatch(r -> r.path().endsWith("/e_archives")
				&& r.body().contains("\"url\":\"https://www.trendyol.com\"")
				&& r.body().contains("\"payment_type\":\"ODEMEARACISI\"")
				&& r.body().contains("\"payment_platform\":\"Trendyol\""));
			assertThat(parasut.invoices().values()).anyMatch(i -> "TY-9001".equals(attributes(i).get("order_no")));

			JsonNode listed = data(api.get(base(connection) + "/invoices"));
			assertThat(listed).hasSize(3);
			assertThat(listed.get(0).get("invoice_number").asString()).isEqualTo(archived.get("number").asString());

			// Resmilesmis fatura bizde iptal edilir; Parasut'ten iptal edilmesi gerektigi yazilir
			api.post("/api/invoices/" + first.get("id").asLong() + "/cancel", Map.of("reason", "Yanlış adres"));
			JsonNode afterCancel = exportOf(first.get("id").asLong(), connection);
			assertThat(afterCancel.get("status").asString()).isEqualTo("formalized");
			assertThat(afterCancel.get("error").asString()).contains("Paraşüt'ten iptal edin");

			pause(connection);
		}
	}

	@Test
	void failedStepsAreRecordedAndRetriedWhereTheyStopped() {
		try (FakeParasut parasut = new FakeParasut().contact("77", "Ege Zeytin A.Ş.", "9876543210")
			.failInvoices("Fatura tarihi kapalı döneme denk geliyor.")) {
			long connection = connect(parasut, "Paraşüt deneme", true, false);
			long customer = customer("Ege Zeytin A.Ş.", "9876543210", null);

			JsonNode invoice = issue(order(customer, Map.of(4, 1)));
			long invoiceId = invoice.get("id").asLong();
			JsonNode failed = exportOf(invoiceId, connection);
			assertThat(failed.get("status").asString()).isEqualTo("failed");
			assertThat(failed.get("error").asString()).contains("Paraşüt hata döndü (422)")
				.contains("Fatura tarihi kapalı döneme denk geliyor.");
			assertThat(parasut.contacts()).as("var olan cari vergi numarasiyla bulunur").hasSize(1);

			parasut.failInvoices(null);
			assertThat(data(api.post(base(connection) + "/invoices/retry", Map.of())).get("sent").asInt())
				.isEqualTo(1);
			JsonNode sent = exportOf(invoiceId, connection);
			assertThat(sent.get("status").asString()).isEqualTo("sent");
			assertThat(sent.get("external_id").asString()).isNotBlank();
			assertThat(parasut.invoices()).hasSize(1);
			assertThat(parasut.invoices().values().iterator().next().get("contact")).isEqualTo("77");

			// Resmilestirme sonradan acilir: reddedilen is yeniden baslatilir, fatura yeniden olusturulmaz
			api.patch(base(connection), Map.of("e_document_enabled", true));
			parasut.rejectFormalization("Alıcı adresi eksik.").runningPolls(1);
			JsonNode rejected = data(api.post(base(connection) + "/invoices/" + invoiceId + "/send", Map.of()));
			assertThat(rejected.get("status").asString()).isEqualTo("failed");
			assertThat(rejected.get("error").asString()).isEqualTo("Resmileştirme reddedildi: Alıcı adresi eksik.");
			parasut.rejectFormalization(null);
			JsonNode formalized = data(api.post(base(connection) + "/invoices/" + invoiceId + "/send", Map.of()));
			assertThat(formalized.get("status").asString()).isEqualTo("formalized");
			assertThat(formalized.get("e_document_type").asString()).as("e-fatura mukellefi degil")
				.isEqualTo("e_archive");
			assertThat(parasut.invoices()).hasSize(1);
			assertThat(parasut.requests()).anyMatch(r -> r.path().endsWith("/e_archives")
				&& !r.body().contains("internet_sale"));

			// Resmilesmemis fatura bizde iptal edilince Parasut'te de iptal edilir
			api.patch(base(connection), Map.of("e_document_enabled", false));
			JsonNode other = issue(order(customer, Map.of(1, 3)));
			long otherId = other.get("id").asLong();
			assertThat(exportOf(otherId, connection).get("status").asString()).isEqualTo("sent");
			api.post("/api/invoices/" + otherId + "/cancel", Map.of("reason", "Müşteri vazgeçti"));
			JsonNode cancelled = exportOf(otherId, connection);
			assertThat(cancelled.get("status").asString()).isEqualTo("cancelled");
			assertThat(parasut.invoices().get(cancelled.get("external_id").asString()).get("cancelled"))
				.isEqualTo(true);

			pause(connection);
		}
	}

	@Test
	void parasutIsAnAccountingConnectionWithoutSalesChannelWork() {
		try (FakeParasut parasut = new FakeParasut()) {
			JsonNode providers = data(api.get("/api/integrations/providers"));
			JsonNode info = providers.get(3);
			assertThat(info.get("key").asString()).isEqualTo("parasut");
			assertThat(info.get("accounting").asBoolean()).isTrue();
			assertThat(info.get("fields")).extracting(f -> f.get("key").asString())
				.containsExactly("company_id", "client_id", "client_secret", "username", "password");

			Response created = api.post("/api/integrations/connections", Map.of("provider", "parasut", "name",
				"Paraşüt yanlış", "base_url", parasut.url(), "order_pull_enabled", true, "credentials",
				credentials("yanlis")));
			assertThat(created.status()).as(created.body()).isEqualTo(201);
			JsonNode connection = data(created);
			assertThat(connection.get("order_pull_enabled").asBoolean()).as("muhasebede siparis yok").isFalse();
			assertThat(connection.get("invoice_push_enabled").asBoolean()).isFalse();
			assertThat(connection.get("credentials").get("password").asString()).startsWith("••••");
			long id = connection.get("id").asLong();

			JsonNode check = data(api.post(base(id) + "/check", Map.of()));
			assertThat(check.get("ok").asBoolean()).isFalse();
			assertThat(check.get("message").asString()).contains("401");

			Response catalog = api.post(base(id) + "/catalog/refresh", Map.of());
			assertThat(catalog.status()).isEqualTo(422);
			assertThat(catalog.json().get("detail").asString()).contains("muhasebe bağlantısı");
			pause(id);
		}
	}

	private long connect(FakeParasut parasut, String name, boolean invoices, boolean eDocuments) {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "parasut", "name", name,
			"base_url", parasut.url(), "invoice_push_enabled", invoices, "e_document_enabled", eDocuments,
			"credentials", credentials(FakeParasut.PASSWORD)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		return data(created).get("id").asLong();
	}

	private static Map<String, String> credentials(String password) {
		return Map.of("company_id", FakeParasut.COMPANY, "client_id", FakeParasut.CLIENT_ID, "client_secret",
			FakeParasut.CLIENT_SECRET, "username", FakeParasut.USERNAME, "password", password);
	}

	private long customer(String name, String taxNumber, String email) {
		Map<String, Object> body = new HashMap<>(Map.of("name", name, "addresses", List.of(Map.of("label",
			"Merkez", "city", "İzmir", "district", "Bornova", "full_address", "Kazımdirik Mh. 372 Sk. No: 4"))));
		if (taxNumber != null) {
			body.put("tax_number", taxNumber);
			body.put("tax_office", "Bornova");
		}
		if (email != null) {
			body.put("email", email);
		}
		Response created = api.post("/api/customers", body);
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		return data(created).get("id").asLong();
	}

	/** Onaylanmis siparis. */
	private long order(long customer, Map<Integer, Integer> items) {
		long address = jdbc.queryForObject("select id from customer_addresses where customer_id = ? order by id"
			+ " limit 1", Long.class, customer);
		Response created = api.post("/api/orders", Map.of("customer_id", customer, "delivery_address_id", address,
			"order_date", "2026-11-27", "items", items.entrySet().stream().map(e -> Map.of("finished_product_id",
				e.getKey(), "quantity", e.getValue())).toList()));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		long order = data(created).get("id").asLong();
		assertThat(api.patch("/api/orders/" + order + "/status", Map.of("status", "confirmed")).status())
			.isEqualTo(200);
		return order;
	}

	private JsonNode issue(long order) {
		Response issued = api.post("/api/orders/" + order + "/invoice", Map.of());
		assertThat(issued.status()).as(issued.body()).isEqualTo(201);
		return data(issued);
	}

	private JsonNode exportOf(long invoiceId, long connection) {
		for (JsonNode export : data(api.get("/api/integrations/invoices/" + invoiceId))) {
			if (export.get("connection_id").asLong() == connection) {
				return export;
			}
		}
		throw new AssertionError("Fatura " + invoiceId + " baglanti " + connection + "'e gonderilmedi");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> attributes(Map<String, Object> invoice) {
		return (Map<String, Object>) invoice.get("attributes");
	}

	/** Sonraki testlerin faturalari bu (kapanacak) sahte sunucuya gitmesin. */
	private void pause(long connection) {
		api.patch(base(connection), Map.of("status", "paused"));
	}

	private static String base(long connection) {
		return "/api/integrations/connections/" + connection;
	}

}
