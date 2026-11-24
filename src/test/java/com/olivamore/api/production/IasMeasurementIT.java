package com.olivamore.api.production;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.BatchFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IAS olcumu (docs/GREENOPS_FEATURES.md 3): asamadan bagimsiz; zeytin ve pirina materyaline yag, nem, KPY (verilmezse
 * hesaplanir), numune no; zeytinde asitlik ve endustriyel randiman. Pirina sikimdan once olculemez; sikimdan sonra
 * gelen zeytin sonucu tuketilmis zeytine yazilir. Hat 1: 3 Ikinci Tartim, 5 Sikim.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IasMeasurementIT {

	private final AtomicInteger codes = new AtomicInteger();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	BatchFlow flow;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		flow = new BatchFlow(api, jdbc);
	}

	@Test
	void oliveMeasurementWritesTheValuesAndComputesKpy() {
		long batch = flow.batch(code());
		flow.completeUpTo(batch, 3);

		Response r = measure(batch, Map.of("kind", "olive", "oil_ratio", 20, "moisture", 50, "acidity", "0.4",
			"industrial_yield", 17, "sample_no", "IAS-1"));

		assertThat(r.status()).as(r.body()).isEqualTo(200);
		long olive = material(batch, "olive");
		// 20 / (100 - 50) x 100
		assertThat(decimal(olive, "kpy")).isEqualByComparingTo("40");
		assertThat(decimal(olive, "oil_ratio")).isEqualByComparingTo("20");
		assertThat(decimal(olive, "moisture")).isEqualByComparingTo("50");
		assertThat(decimal(olive, "acidity")).isEqualByComparingTo("0.4");
		assertThat(decimal(olive, "industrial_yield")).isEqualByComparingTo("17");
		assertThat(text(olive, "sample_no")).isEqualTo("IAS-1");
	}

	@Test
	void pomaceIsMeasuredOnlyAfterPressingAndWithoutAcidity() {
		long batch = flow.batch(code());
		flow.completeUpTo(batch, 3);

		Response early = measure(batch, Map.of("kind", "pomace", "oil_ratio", 3, "moisture", 60));
		flow.completeUpTo(batch, 5);
		Response withAcidity = measure(batch, Map.of("kind", "pomace", "oil_ratio", 3, "moisture", 60,
			"acidity", "0.3"));
		Response ok = measure(batch, Map.of("kind", "pomace", "oil_ratio", 3, "moisture", 60));

		assertThat(early.status()).as(early.body()).isEqualTo(422);
		assertThat(early.json().get("detail").asString()).contains("pirina yok");
		assertThat(withAcidity.status()).isEqualTo(422);
		assertThat(withAcidity.json().get("errors").has("acidity")).isTrue();
		assertThat(ok.status()).as(ok.body()).isEqualTo(200);
		// 3 / (100 - 60) x 100
		assertThat(decimal(material(batch, "pomace"), "kpy")).isEqualByComparingTo("7.5");
	}

	@Test
	void lateOliveResultGoesToTheConsumedOliveAndAGivenKpyIsKept() {
		long batch = flow.batch(code());
		flow.completeUpTo(batch, 5);

		Response r = measure(batch, Map.of("kind", "olive", "oil_ratio", 18, "moisture", 48, "kpy", "35.1"));

		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(decimal(material(batch, "olive"), "kpy")).isEqualByComparingTo("35.1");
	}

	@Test
	void unknownKindAndFullMoistureAreRejected() {
		long batch = flow.batch(code());
		flow.completeUpTo(batch, 3);

		Response kind = measure(batch, Map.of("kind", "oil", "oil_ratio", 18, "moisture", 48));
		Map<String, Object> wet = new HashMap<>(Map.of("kind", "olive", "oil_ratio", 18, "moisture", 100));
		Response moisture = measure(batch, wet);

		assertThat(kind.status()).isEqualTo(422);
		assertThat(kind.json().get("errors").has("kind")).isTrue();
		assertThat(moisture.status()).isEqualTo(422);
		assertThat(moisture.json().get("errors").has("moisture")).isTrue();
	}

	private Response measure(long batch, Map<String, Object> body) {
		return api.post("/api/production-batches/" + batch + "/ias-measurements", body);
	}

	/** Partinin o urundeki en son materyali. */
	private long material(long batch, String productKey) {
		return jdbc.queryForObject("""
			select m.id from materials m
			join production_product_types t on t.id = m.production_product_type_id
			join production_products p on p.id = t.production_product_id
			where m.production_batch_id = ? and p.key = ? order by m.id desc limit 1
			""", Long.class, batch, productKey);
	}

	private BigDecimal decimal(long material, String key) {
		List<BigDecimal> values = jdbc.queryForList(valueSql("value_decimal"), BigDecimal.class, material, key);
		return values.isEmpty() ? null : values.getFirst();
	}

	private String text(long material, String key) {
		List<String> values = jdbc.queryForList(valueSql("value_text"), String.class, material, key);
		return values.isEmpty() ? null : values.getFirst();
	}

	private static String valueSql(String column) {
		return "select v." + column + " from material_attribute_values v"
			+ " join production_product_attribute_definitions d on d.id = v.production_product_attribute_definition_id"
			+ " join attribute_definitions a on a.id = d.attribute_definition_id"
			+ " where v.material_id = ? and a.attribute_key = ?";
	}

	private String code() {
		return "IAS-" + codes.incrementAndGet();
	}

}
