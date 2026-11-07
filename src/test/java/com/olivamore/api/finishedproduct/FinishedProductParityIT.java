package com.olivamore.api.finishedproduct;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 74 tarif ve bitmis urun durumu (src/test/resources/laravel/finished-product.json,
 * reference/capture_finished_product.py): tarif versiyonlama, oran toplami, urun depo dokumu, hacim korumasi,
 * silme korumalari. Laravel'in 500 verdigi yedi durum Java'da 422 (DEVIATIONS 14).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// veri degistiriyor ve alfabetik sirada diger modullerin testlerinden once calisiyor: sonraki sinif temiz seed'li
// yeni bir veritabaniyla baslasin
@DirtiesContext
class FinishedProductParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/finished-product.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			// ayni saniyede olusan tarifler: latest() esitlikte sirasiz
			.unordered("tarif liste sonra", "data")
			.deviation("tarif ekle hatali", 422, """
				{"message":"The name field must be a string. (and 9 more errors)","errors":{"name":["The name field must be a string."],"contents.0.production_product_type_id":["The selected contents.0.production_product_type_id is invalid."],"contents.1.production_product_type_id":["The contents.1.production_product_type_id field is required."],"contents.0.ratio":["The contents.0.ratio field must not be greater than 1."],"contents.1.ratio":["The contents.1.ratio field must be a number.","The contents.1.ratio field must not be greater than 1."],"attribute_rules.0.attribute_definition_id":["The selected attribute_rules.0.attribute_definition_id is invalid."],"attribute_rules.0.value":["The attribute_rules.0.value field must be at least 0."],"attribute_rules.0.min_deviation":["The attribute_rules.0.min_deviation field must be a number."],"contents":["İçerik oranlarının toplamı 1.00 olmalıdır. Mevcut toplam: 2"]}}""")
			.deviation("tarif ekle oran dizi", 422, """
				{"message":"The contents.0.ratio field must be a number. (and 1 more error)","errors":{"contents.0.ratio":["The contents.0.ratio field must be a number."],"contents":["İçerik oranlarının toplamı 1.00 olmalıdır. Mevcut toplam: 0"]}}""")
			.deviation("tarif ekle oran metin", 422, """
				{"message":"The contents.0.ratio field must be a number. (and 2 more errors)","errors":{"contents.0.ratio":["The contents.0.ratio field must be a number.","The contents.0.ratio field must not be greater than 1."],"contents":["İçerik oranlarının toplamı 1.00 olmalıdır. Mevcut toplam: 0"]}}""")
			.deviation("tarif ekle tekrar tip", 422, """
				{"message":"The contents.0.production_product_type_id field has a duplicate value. (and 1 more error)","errors":{"contents.0.production_product_type_id":["The contents.0.production_product_type_id field has a duplicate value."],"contents.1.production_product_type_id":["The contents.1.production_product_type_id field has a duplicate value."]}}""")
			.deviation("tarif ekle tekrar attribute", 422, """
				{"message":"The attribute_rules.0.attribute_definition_id field has a duplicate value. (and 1 more error)","errors":{"attribute_rules.0.attribute_definition_id":["The attribute_rules.0.attribute_definition_id field has a duplicate value."],"attribute_rules.1.attribute_definition_id":["The attribute_rules.1.attribute_definition_id field has a duplicate value."]}}""")
			.deviation("urun ekle tekrar kalem", 422, """
				{"message":"The components.0.packaging_item_id field has a duplicate value. (and 1 more error)","errors":{"components.0.packaging_item_id":["The components.0.packaging_item_id field has a duplicate value."],"components.1.packaging_item_id":["The components.1.packaging_item_id field has a duplicate value."]}}""")
			.deviation("urun sil siparisli", 422, """
				{"message":"Bu son ürün silinemez; kullanıldığı yerler: 4 sipariş kalemi.","errors":{}}""")
			.run();
		assertThat(result.steps()).isEqualTo(74);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
