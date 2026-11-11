package com.olivamore.api.sales;

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

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Siparis kurallari: durum gecisleri (draft -> confirmed -> stock_check -> ready -> shipped), hazirlikta raftan
 * toplama, sevkten sonra kilit, teslimat adresinin sahibi, silme ve yetki.
 * Demo veri: urun 1 raflarda stoklu, urun 19 raflarda yok; musteri 1 ve 2'nin adresleri.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderRulesIT {

	private static final int STOCKED = 1;
	private static final int NOT_ON_SHELVES = 19;

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
	void statusCannotSkipSteps() {
		long order = order(1, STOCKED, 2);

		Response r = status(order, "ready");

		assertRule(r, "'draft' durumundan 'ready' durumuna geçiş yapılamaz.");
	}

	@Test
	void readyPicksFromShelvesAndShippingLocksTheOrder() {
		long order = order(1, STOCKED, 4);
		advance(order, "confirmed", "stock_check");

		Response ready = api.patch("/api/orders/" + order + "/status", Map.of("status", "ready", "picks", List.of()));

		assertThat(ready.status()).as(ready.body()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from finished_product_stock_movements"
			+ " where order_id = ? and movement_type = 'order_pick'", Long.class, order)).isEqualTo(4);
		assertThat(data(status(order, "shipped")).get("allowed_transitions").isEmpty()).isTrue();
		assertRule(api.patch("/api/orders/" + order, Map.of("note", "x")), "Sipariş 'shipped' durumundayken düzenlenemez.");
		assertRule(status(order, "cancelled"), "'shipped' durumundan 'cancelled' durumuna geçiş yapılamaz.");
	}

	@Test
	void readyNeedsEnoughStockOnShelves() {
		long order = order(2, NOT_ON_SHELVES, 1);
		advance(order, "confirmed", "stock_check");

		Response r = status(order, "ready");

		assertThat(r.status()).isEqualTo(422);
		assertThat(r.json().get("detail").asString())
			.startsWith("Portakallı Zeytinyağı 500ml için raflarda yeterli adet yok: 1 adet eksik.");
		assertThat(status(order, "in_production").status()).isEqualTo(200);
	}

	@Test
	void deliveryAddressMustBelongToTheCustomer() {
		Response r = api.post("/api/orders", body(1, address(2), STOCKED, 1));

		assertRule(r, "Teslimat adresi bu müşteriye ait değil.");
	}

	@Test
	void onlyADraftOrderCanBeDeleted() {
		long draft = order(1, STOCKED, 1);
		long confirmed = order(1, STOCKED, 1);
		advance(confirmed, "confirmed");

		assertRule(api.delete("/api/orders/" + confirmed), "Sadece taslak (draft) durumdaki siparişler silinebilir.");
		assertThat(api.delete("/api/orders/" + draft).status()).isEqualTo(200);
		assertThat(api.get("/api/orders/" + draft).status()).isEqualTo(404);
	}

	@Test
	void operatorCannotCreateAnOrder() {
		Response r = api.as("operator1@olivamore.com").post("/api/orders", body(1, address(1), STOCKED, 1));

		assertThat(r.status()).isEqualTo(403);
	}

	// ---- yardimcilar -------------------------------------------------------

	private long order(long customer, int product, int quantity) {
		Response r = api.post("/api/orders", body(customer, address(customer), product, quantity));
		assertThat(r.status()).as(r.body()).isEqualTo(201);
		return id(r);
	}

	private static Map<String, Object> body(long customer, long address, int product, int quantity) {
		return Map.of("customer_id", customer, "delivery_address_id", address, "order_date", "2026-11-07",
			"items", List.of(Map.of("finished_product_id", product, "quantity", quantity)));
	}

	private long address(long customer) {
		return jdbc.queryForObject("select id from customer_addresses where customer_id = ? order by id limit 1",
			Long.class, customer);
	}

	private void advance(long order, String... statuses) {
		for (String s : statuses) {
			Response r = status(order, s);
			assertThat(r.status()).as(s + ": " + r.body()).isEqualTo(200);
		}
	}

	private Response status(long order, String status) {
		return api.patch("/api/orders/" + order + "/status", Map.of("status", status));
	}

	private static void assertRule(Response r, String detail) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).isEqualTo(detail);
	}

}
