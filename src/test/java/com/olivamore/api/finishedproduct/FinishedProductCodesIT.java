package com.olivamore.api.finishedproduct;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bitmis urunun barkodu ve SKU'su (docs/INTEGRATIONS.md 0): pazar yeri ve sitedeki urunle eslestirme anahtari;
 * benzersiz, gelmezse degismez, bos gelirse silinir. Demo urunlerde dolu.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinishedProductCodesIT {

	@LocalServerPort
	int port;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void demoProductsCarryBarcodeAndSku() {
		JsonNode first = data(api.get("/api/finished-products/1"));
		assertThat(first.get("barcode").asString()).isEqualTo("8691234000014");
		assertThat(first.get("sku").asString()).isEqualTo("OLV-0001");
	}

	@Test
	void barcodeAndSkuAreUniqueKeptWhenAbsentAndClearedWhenBlank() {
		Response created = api.post("/api/finished-products", product("Kod Deneme 250ml", " 8690000000011 ", "KOD-1"));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		long id = data(created).get("id").asLong();
		assertThat(data(created).get("barcode").asString()).isEqualTo("8690000000011");

		Response duplicate = api.post("/api/finished-products", product("Kod Deneme 2", "8690000000011", "OLV-0001"));
		assertThat(duplicate.status()).isEqualTo(422);
		assertThat(duplicate.json().get("errors").has("barcode")).isTrue();
		assertThat(duplicate.json().get("errors").has("sku")).isTrue();

		// Alan gelmezse degismez; kendi degeriyle guncellemek benzersizlige takilmaz
		JsonNode renamed = data(api.patch("/api/finished-products/" + id,
			Map.of("name", "Kod Deneme 250 ml", "sku", "KOD-1")));
		assertThat(renamed.get("barcode").asString()).isEqualTo("8690000000011");
		assertThat(renamed.get("sku").asString()).isEqualTo("KOD-1");

		Map<String, Object> clear = new HashMap<>();
		clear.put("barcode", null);
		clear.put("sku", "  ");
		JsonNode cleared = data(api.patch("/api/finished-products/" + id, clear));
		assertThat(cleared.get("barcode").isNull()).isTrue();
		assertThat(cleared.get("sku").isNull()).isTrue();

		Response taken = api.patch("/api/finished-products/" + id, Map.of("barcode", "8691234000014"));
		assertThat(taken.status()).isEqualTo(422);
		Response tooLong = api.patch("/api/finished-products/" + id, Map.of("sku", "X".repeat(65)));
		assertThat(tooLong.status()).isEqualTo(422);
	}

	private static Map<String, Object> product(String name, String barcode, String sku) {
		return Map.of("name", name, "finished_product_recipe_id", 1, "volume_litre", 0.25, "barcode", barcode,
			"sku", sku, "components", List.of(Map.of("packaging_item_id", 1, "quantity", 1),
				Map.of("packaging_item_id", 8, "quantity", 1)));
	}

}
