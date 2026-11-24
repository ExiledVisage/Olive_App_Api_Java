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
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Zeytin alim fiyati ve kanali (docs/GREENOPS_FEATURES.md 1): yalnizca teslimata girilir, KDV haric fiyat ve oran;
 * tutarlar KDV haric ve dahil, grupta kilo payiyla; fiyat yetkisi olmayan (uretim iscisi) goremez ve giremez,
 * planlamaci gorur; tedarikci teslimatlarinda donem ve alim toplamlari.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseIT {

	private static final String WORKER = "uretim@olivamore.com";
	private static final String PLANNER = "planner@olivamore.com";

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
	void deliveryPurchaseShowsTotalsWithAndWithoutVat() {
		long delivery = flow.weighedDelivery(code(), 1000);

		JsonNode purchase = data(put(delivery, purchase("40.00", "1", "500"))).get("purchase");

		assertThat(purchase.get("supply_channel").asString()).isEqualTo("trader");
		assertThat(purchase.get("price_per_kg").decimalValue()).isEqualByComparingTo("40");
		assertThat(purchase.get("price_per_kg_with_vat").decimalValue()).isEqualByComparingTo("40.40");
		// 1000 x 40 + 500 nakliye; KDV dahil 1000 x 40.40 + 500
		assertThat(purchase.get("total_ex_vat").decimalValue()).isEqualByComparingTo("40500");
		assertThat(purchase.get("total_with_vat").decimalValue()).isEqualByComparingTo("40900");
		assertThat(purchase.get("cost_per_kg_ex_vat").decimalValue()).isEqualByComparingTo("40.5");
		assertThat(purchase.get("cost_per_kg_with_vat").decimalValue()).isEqualByComparingTo("40.9");
		assertThat(purchase.get("from_delivery").asBoolean()).isFalse();
	}

	@Test
	void groupShowsTheDeliveryPriceWithItsKgShareAndCannotTakeAPrice() {
		long delivery = flow.weighedDelivery(code(), 5000);
		put(delivery, purchase("40.00", "1", "500"));
		flow.completeUpTo(delivery, 3);
		Response split = api.post("/api/production-batches/" + delivery + "/groups",
			Map.of("quantity_kg", 300, "start_stage_id", 2));
		assertThat(split.status()).as(split.body()).isEqualTo(200);
		long group = data(split).get("id").asLong();

		JsonNode purchase = data(api.get("/api/production-batches/" + group)).get("purchase");

		assertThat(purchase.get("from_delivery").asBoolean()).isTrue();
		// nakliye 500 x 300 / 5000; 300 x 40 + 30
		assertThat(purchase.get("transport_cost").decimalValue()).isEqualByComparingTo("30");
		assertThat(purchase.get("total_ex_vat").decimalValue()).isEqualByComparingTo("12030");
		Response onGroup = api.put("/api/production-batches/" + group + "/purchase", purchase("41", "1", null));
		assertThat(onGroup.status()).as(onGroup.body()).isEqualTo(422);
	}

	@Test
	void priceNeedsAVatRateAndOwnGroveNeedsNoPrice() {
		long delivery = flow.weighedDelivery(code(), 1000);

		Response noVat = api.put("/api/production-batches/" + delivery + "/purchase", Map.of("price_per_kg", 40));
		Response ownGrove = api.put("/api/production-batches/" + delivery + "/purchase",
			Map.of("supply_channel", "own_grove"));

		assertThat(noVat.status()).as(noVat.body()).isEqualTo(422);
		assertThat(noVat.json().get("errors").has("vat_rate")).isTrue();
		assertThat(ownGrove.status()).as(ownGrove.body()).isEqualTo(200);
		JsonNode purchase = data(ownGrove).get("purchase");
		assertThat(purchase.get("supply_channel").asString()).isEqualTo("own_grove");
		assertThat(purchase.get("total_ex_vat").isNull()).isTrue();
	}

	@Test
	void workerNeitherSeesNorEntersPricesButThePlannerSees() {
		long delivery = flow.weighedDelivery(code(), 1000);
		put(delivery, purchase("40.00", "1", null));

		Response asWorker = api.as(WORKER).get("/api/production-batches/" + delivery);
		Response workerPut = api.as(WORKER).put("/api/production-batches/" + delivery + "/purchase",
			purchase("1", "1", null));
		Response asPlanner = api.as(PLANNER).get("/api/production-batches/" + delivery);

		assertThat(asWorker.status()).as(asWorker.body()).isEqualTo(200);
		assertThat(data(asWorker).has("purchase")).isFalse();
		assertThat(workerPut.status()).isEqualTo(403);
		assertThat(data(asPlanner).get("purchase").get("price_per_kg").decimalValue()).isEqualByComparingTo("40");
	}

	@Test
	void supplierDeliveriesCarryPurchasesAndPeriodTotals() {
		long delivery = flow.weighedDelivery(code(), 1000);
		put(delivery, purchase("40.00", "1", "500"));
		String today = LocalDate.now(ZoneOffset.UTC).toString();

		Response r = api.get("/api/suppliers/1/deliveries?date_from=" + today + "&date_to=" + today);

		assertThat(r.status()).as(r.body()).isEqualTo(200);
		JsonNode row = r.json().get("data").valueStream()
			.filter(d -> d.get("production_batch_id").asLong() == delivery).findFirst().orElseThrow();
		assertThat(row.get("purchase").get("total_with_vat").decimalValue()).isEqualByComparingTo("40900");
		JsonNode summary = r.json().get("summary").get("purchase");
		assertThat(summary.get("priced_count").asInt()).isPositive();
		assertThat(summary.get("total_ex_vat").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("40500"));
		assertThat(api.get("/api/suppliers/1/deliveries?date_from=2001-01-01&date_to=2001-01-02").json()
			.get("data")).isEmpty();
		assertThat(api.get("/api/suppliers/1/deliveries?date_from=dun").status()).isEqualTo(422);
	}

	private Response put(long batch, Map<String, Object> body) {
		Response r = api.put("/api/production-batches/" + batch + "/purchase", body);
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		return r;
	}

	private static Map<String, Object> purchase(String price, String vat, String transport) {
		Map<String, Object> body = new HashMap<>();
		body.put("supply_channel", "trader");
		body.put("price_per_kg", new BigDecimal(price));
		body.put("vat_rate", new BigDecimal(vat));
		if (transport != null) {
			body.put("transport_cost", new BigDecimal(transport));
		}
		return body;
	}

	private String code() {
		return "PRC-" + codes.incrementAndGet();
	}

}
