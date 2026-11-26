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

import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entegrasyon baglantilari (docs/INTEGRATIONS.md 0): platform listesi, WooCommerce baglantisi (sahte site), erisim
 * bilgilerinin sifreli saklanmasi ve maskeli donmesi, kismi guncelleme, baglanti denemesi, dogrulama, yetki.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationConnectionIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	FakeWooCommerce site;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		site = new FakeWooCommerce().product(11, "Gemlik Sızma 1 L", "OLV-0004", "8691234000045", 5);
	}

	@AfterAll
	void tearDown() {
		site.close();
	}

	@Test
	void providersListEveryPlatformAsSupported() {
		JsonNode providers = data(api.get("/api/integrations/providers"));
		assertThat(providers).extracting(p -> p.get("key").asString())
			.containsExactly("woocommerce", "trendyol", "hepsiburada");
		assertThat(providers.get(0).get("supported").asBoolean()).isTrue();
		assertThat(providers.get(0).get("needs_base_url").asBoolean()).isTrue();
		assertThat(providers.get(0).get("fields")).extracting(f -> f.get("key").asString())
			.containsExactly("consumer_key", "consumer_secret");
		assertThat(providers.get(1).get("supported").asBoolean()).as("Trendyol").isTrue();
		assertThat(providers.get(1).get("needs_base_url").asBoolean()).as("pazar yerinde adres istege bagli").isFalse();
		assertThat(providers.get(2).get("supported").asBoolean()).as("Hepsiburada").isTrue();
		assertThat(providers.get(2).get("fields")).extracting(f -> f.get("key").asString())
			.containsExactly("merchant_id", "username", "password", "integrator");
	}

	@Test
	void wooCommerceConnectionIsStoredEncryptedReturnedMaskedAndChecked() {
		Response created = api.post("/api/integrations/connections", Map.of("provider", "woocommerce", "name",
			"Olivamore site", "base_url", site.url() + "/", "credentials", Map.of("consumer_key", FakeWooCommerce.KEY,
				"consumer_secret", FakeWooCommerce.SECRET)));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		JsonNode connection = data(created);
		long id = connection.get("id").asLong();
		assertThat(connection.get("base_url").asString()).isEqualTo(site.url());
		assertThat(connection.get("status").asString()).isEqualTo("active");
		assertThat(connection.get("order_pull_enabled").asBoolean()).isTrue();
		assertThat(connection.get("stock_push_enabled").asBoolean()).as("stok gonderimi baslangicta kapali").isFalse();
		assertThat(connection.get("credentials").get("consumer_key").asString()).isEqualTo("ck_••••cdef");
		assertThat(created.body()).doesNotContain(FakeWooCommerce.SECRET);

		String stored = jdbc.queryForObject("select credentials_encrypted from integration_connections where id = ?",
			String.class, id);
		assertThat(stored).doesNotContain(FakeWooCommerce.KEY).doesNotContain(FakeWooCommerce.SECRET);

		JsonNode check = data(api.post("/api/integrations/connections/" + id + "/check", Map.of()));
		assertThat(check.get("ok").asBoolean()).as(check.toString()).isTrue();
		assertThat(check.get("message").asString()).contains("TRY");
		assertThat(site.requests()).extracting(FakeWooCommerce.Request::path).contains("/products?per_page=1");

		// Yalniz gizli anahtar degisir; tuketici anahtari korunur
		JsonNode wrong = data(api.patch("/api/integrations/connections/" + id, Map.of("credentials",
			Map.of("consumer_secret", "cs_wrong_000000000000"), "status", "paused")));
		assertThat(wrong.get("credentials").get("consumer_key").asString()).isEqualTo("ck_••••cdef");
		assertThat(wrong.get("status").asString()).isEqualTo("paused");
		JsonNode rejected = data(api.post("/api/integrations/connections/" + id + "/check", Map.of()));
		assertThat(rejected.get("ok").asBoolean()).isFalse();
		assertThat(rejected.get("message").asString()).contains("401");

		assertThat(api.delete("/api/integrations/connections/" + id).status()).isEqualTo(204);
		assertThat(api.get("/api/integrations/connections/" + id).status()).isEqualTo(404);
	}

	@Test
	void validationAndPermissions() {
		Response missing = api.post("/api/integrations/connections", Map.of("provider", "woocommerce", "name", "Eksik",
			"credentials", Map.of("consumer_key", "ck_x")));
		assertThat(missing.status()).isEqualTo(422);
		assertThat(missing.json().get("errors").has("base_url")).isTrue();
		assertThat(missing.json().get("errors").has("credentials.consumer_secret")).isTrue();

		Response unknown = api.post("/api/integrations/connections", Map.of("provider", "amazon", "name", "Bilinmeyen",
			"credentials", Map.of("x", "1")));
		assertThat(unknown.status()).isEqualTo(422);
		assertThat(unknown.json().get("errors").has("provider")).isTrue();

		Response badUrl = api.post("/api/integrations/connections", Map.of("provider", "woocommerce", "name", "Kotu",
			"base_url", "ftp://magaza", "credentials", Map.of("consumer_key", "ck_x", "consumer_secret", "cs_y")));
		assertThat(badUrl.status()).isEqualTo(422);
		assertThat(badUrl.json().get("errors").has("base_url")).isTrue();

		assertThat(api.as("depo@olivamore.com").get("/api/integrations/connections").status()).isEqualTo(403);
		assertThat(api.as("depo@olivamore.com").post("/api/integrations/connections", Map.of()).status())
			.isEqualTo(403);
	}

}
