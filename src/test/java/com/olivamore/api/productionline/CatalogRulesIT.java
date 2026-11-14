package com.olivamore.api.productionline;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Katalogda ic ice kaynaklar: kategorinin attribute eslemeleri kategori izinleriyle korunur, baska kategorinin
 * eslemesi ya da baska hattin asamasi 404. Demo veri: esleme 1 kategori 1'in, asama 1 hat 1'in; operator
 * kategoriyi gorur ama yonetemez, depo kullanicisi goremez.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogRulesIT {

	private static final String OPERATOR = "operator1@olivamore.com";
	private static final String WAREHOUSE = "depo@olivamore.com";
	private static final String MAPPINGS = "/api/production-products/1/attribute-definitions";

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
	void attributeMappingsNeedProductTypePermissions() {
		Map<String, Object> mapping = Map.of("attribute_key", "harvest_note", "label", "Hasat notu",
			"data_type", "text");

		assertThat(api.as(WAREHOUSE).get(MAPPINGS).status()).isEqualTo(403);
		assertThat(api.as(WAREHOUSE).get(MAPPINGS + "/1").status()).isEqualTo(403);
		assertThat(api.as(OPERATOR).get(MAPPINGS).status()).isEqualTo(200);
		assertThat(api.as(OPERATOR).get(MAPPINGS + "/1").status()).isEqualTo(200);
		assertThat(api.as(OPERATOR).post(MAPPINGS, mapping).status()).isEqualTo(403);
		assertThat(api.as(OPERATOR).patch(MAPPINGS + "/1", Map.of("is_required", true)).status()).isEqualTo(403);
		assertThat(api.as(OPERATOR).delete(MAPPINGS + "/1").status()).isEqualTo(403);
		assertThat(jdbc.queryForObject("select is_required from production_product_attribute_definitions where id = 1",
			Boolean.class)).isFalse();

		Response created = api.post(MAPPINGS, mapping);
		assertThat(created.status()).as(created.body()).isEqualTo(200);
	}

	@Test
	void mappingOfAnotherProductIsNotFound() {
		String foreign = "/api/production-products/2/attribute-definitions/1";

		for (Response r : List.of(api.get(foreign), api.patch(foreign, Map.of("is_required", true)),
			api.delete(foreign))) {
			assertThat(r.status()).as(r.body()).isEqualTo(404);
			assertThat(r.json().get("detail").asString())
				.isEqualTo("Bu attribute eşlemesi bu ürün kategorisine ait değil.");
		}
		assertThat(jdbc.queryForObject("select count(*) from production_product_attribute_definitions where id = 1",
			Long.class)).isEqualTo(1);
	}

	@Test
	void stageExtensionsOfAnotherLineAreNotFound() {
		List<Long> before = extensionsOfStage1();

		Response r = api.put("/api/production-lines/2/stages/1/extensions",
			Map.of("extensions", List.of(Map.of("stage_extension_type_id", 1))));

		assertThat(r.status()).as(r.body()).isEqualTo(404);
		assertThat(extensionsOfStage1()).isEqualTo(before);
	}

	private List<Long> extensionsOfStage1() {
		return jdbc.queryForList("select id from production_stage_extensions where production_stage_id = 1 order by id",
			Long.class);
	}

}
