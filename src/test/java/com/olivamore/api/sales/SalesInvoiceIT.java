package com.olivamore.api.sales;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Satis faturasi (docs/INVOICES.md): siparis kaleminin fiyati (verilmezse urunun liste fiyati) ve KDV orani, KDV
 * dahil tutardan net ve KDV, oran basina dokum, yil icinde sirali numara, alici bilgisinin saklanmasi, siparis basina
 * tek gecerli fatura, faturali siparisin kilidi, iptal ve yeniden kesme, fiyatsiz urun, dogrulama ve yetki.
 * Demo veri: urun 4 (1 L) 600 TL, urun 1 (250 ml) 150 TL, urun 20 (750 ml aromali) 525 TL; hepsi %1 KDV.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesInvoiceIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
		// Iki oranli fatura icin urun 1 bu testte %20
		jdbc.update("update finished_products set vat_rate = 20 where id = 1");
	}

	@AfterAll
	void tearDown() {
		jdbc.update("update finished_products set vat_rate = 1, sale_price = 150.00 where id = 1");
		jdbc.update("update finished_products set sale_price = 525.00 where id = 20");
		jdbc.update("update customers set tax_number = null, tax_office = null where id = 1");
	}

	@Test
	void orderIsInvoicedWithVatSplitAndLockedUntilTheInvoiceIsCancelled() {
		Response taxed = api.patch("/api/customers/1", Map.of("tax_number", "1234567890", "tax_office", "Kadıköy"));
		assertThat(taxed.status()).as(taxed.body()).isEqualTo(200);
		assertThat(data(taxed).get("tax_number").asString()).isEqualTo("1234567890");

		Response created = api.post("/api/orders", Map.of("customer_id", 1, "delivery_address_id", address(1),
			"order_date", "2026-11-27", "items", List.of(Map.of("finished_product_id", 4, "quantity", 2),
				Map.of("finished_product_id", 1, "quantity", 3, "unit_price", 149.90))));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		long order = id(created);
		JsonNode items = data(created).get("items");
		assertMoney(items.get(0).get("unit_price"), "600.00");
		assertThat(items.get(0).get("vat_rate").asInt()).isEqualTo(1);
		assertMoney(items.get(0).get("line_total"), "1200.00");
		assertMoney(items.get(1).get("unit_price"), "149.90");
		assertThat(items.get(1).get("vat_rate").asInt()).isEqualTo(20);
		assertMoney(data(created).get("total"), "1649.70");
		assertThat(data(created).get("invoice").isNull()).isTrue();

		assertRule(api.post("/api/orders/" + order + "/invoice", Map.of()),
			"Sipariş 'draft' durumundayken fatura kesilemez; önce siparişi onaylayın.");
		status(order, "confirmed");

		Response issued = api.post("/api/orders/" + order + "/invoice", Map.of("note", "Kargo dahil"));
		assertThat(issued.status()).as(issued.body()).isEqualTo(201);
		JsonNode invoice = data(issued);
		long invoiceId = invoice.get("id").asLong();
		String number = invoice.get("number").asString();
		assertThat(number).matches("OLV\\d{13}");
		assertThat(invoice.get("status").asString()).isEqualTo("issued");
		assertThat(invoice.get("issue_date").asString()).matches("\\d{4}-\\d\\d-\\d\\d");
		assertThat(invoice.get("order").get("id").asLong()).isEqualTo(order);
		assertThat(invoice.get("buyer").get("tax_number").asString()).isEqualTo("1234567890");
		assertThat(invoice.get("buyer").get("tax_office").asString()).isEqualTo("Kadıköy");
		assertThat(invoice.get("buyer").get("city").asString()).isNotBlank();
		assertThat(invoice.get("note").asString()).isEqualTo("Kargo dahil");
		assertThat(invoice.get("created_by").get("name").asString()).isNotBlank();
		assertMoney(invoice.get("gross_total"), "1649.70");
		assertMoney(invoice.get("net_total"), "1562.87");
		assertMoney(invoice.get("vat_total"), "86.83");
		assertThat(invoice.get("vat_breakdown")).extracting(g -> g.get("rate").asInt() + ":"
			+ g.get("net").decimalValue().toPlainString() + ":" + g.get("vat").decimalValue().toPlainString())
			.containsExactly("1:1188.12:11.88", "20:374.75:74.95");
		JsonNode lines = invoice.get("lines");
		assertThat(lines).hasSize(2);
		assertThat(lines.get(0).get("description").asString()).isEqualTo("Naturel Sızma Gemlik 0.3 1L");
		assertMoney(lines.get(1).get("gross_amount"), "449.70");

		// Alici bilgisi kesildigi andaki haliyle kalir
		api.patch("/api/customers/1", Map.of("tax_number", "98765432101"));
		assertThat(data(api.get("/api/invoices/" + invoiceId)).get("buyer").get("tax_number").asString())
			.isEqualTo("1234567890");

		assertRule(api.post("/api/orders/" + order + "/invoice", Map.of()),
			"Bu siparişin faturası zaten kesildi (" + number + ").");
		JsonNode detail = data(api.get("/api/orders/" + order));
		assertThat(detail.get("invoice").get("number").asString()).isEqualTo(number);
		assertMoney(detail.get("invoice").get("gross_total"), "1649.70");
		assertRule(api.patch("/api/orders/" + order, Map.of("note", "x")), "Siparişin faturası kesildi (" + number
			+ "). Siparişi düzenlemek için önce faturayı iptal edin.");
		assertRule(status(order, "cancelled"), "Siparişin faturası kesildi (" + number
			+ "). Siparişi iptal etmek için önce faturayı iptal edin.");

		JsonNode listed = api.get("/api/invoices?order_id=" + order).json().get("content");
		assertThat(listed).hasSize(1);
		assertThat(listed.get(0).has("lines")).as("listede kalem yok").isFalse();

		Response noReason = api.post("/api/invoices/" + invoiceId + "/cancel", Map.of());
		assertThat(noReason.status()).isEqualTo(422);
		assertThat(noReason.json().get("errors").has("reason")).isTrue();
		JsonNode cancelled = data(api.post("/api/invoices/" + invoiceId + "/cancel", Map.of("reason",
			"Adres yanlış")));
		assertThat(cancelled.get("status").asString()).isEqualTo("cancelled");
		assertThat(cancelled.get("cancel_reason").asString()).isEqualTo("Adres yanlış");
		assertThat(cancelled.get("cancelled_by").get("name").asString()).isNotBlank();
		assertRule(api.post("/api/invoices/" + invoiceId + "/cancel", Map.of("reason", "x")),
			"Fatura zaten iptal edilmiş.");
		assertThat(data(api.get("/api/orders/" + order)).get("invoice").isNull()).isTrue();

		// Yerine yenisi kesilir: numara siradaki
		JsonNode again = data(api.post("/api/orders/" + order + "/invoice", Map.of()));
		assertThat(Long.parseLong(again.get("number").asString().substring(7)))
			.isEqualTo(Long.parseLong(number.substring(7)) + 1);
		assertThat(again.get("buyer").get("tax_number").asString()).isEqualTo("98765432101");
		assertThat(api.get("/api/invoices?order_id=" + order + "&status=cancelled").json().get("content")).hasSize(1);
	}

	@Test
	void productWithoutPriceBlocksTheInvoice() {
		jdbc.update("update finished_products set sale_price = null where id = 20");
		Response created = api.post("/api/orders", Map.of("customer_id", 2, "delivery_address_id", address(2),
			"order_date", "2026-11-27", "items", List.of(Map.of("finished_product_id", 20, "quantity", 1))));
		long order = id(created);
		assertThat(data(created).get("items").get(0).get("unit_price").isNull()).isTrue();
		assertThat(data(created).get("total").isNull()).isTrue();
		status(order, "confirmed");

		assertRule(api.post("/api/orders/" + order + "/invoice", Map.of()), "Fiyatı olmayan ürün: Portakallı "
			+ "Zeytinyağı 750ml. Ürüne satış fiyatı girin ya da siparişte fiyat yazın.");

		// Urune fiyat girilince fatura kesilir
		Response priced = api.patch("/api/finished-products/20", Map.of("sale_price", 499.90));
		assertThat(priced.status()).as(priced.body()).isEqualTo(200);
		assertMoney(data(priced).get("sale_price"), "499.90");
		JsonNode invoice = data(api.post("/api/orders/" + order + "/invoice", Map.of()));
		assertMoney(invoice.get("gross_total"), "499.90");
		assertMoney(invoice.get("net_total"), "494.95");
	}

	@Test
	void validationAndPermissions() {
		Response badTax = api.patch("/api/customers/2", Map.of("tax_number", "12345"));
		assertThat(badTax.status()).isEqualTo(422);
		assertThat(badTax.json().get("errors").has("tax_number")).isTrue();

		Response badRate = api.patch("/api/finished-products/2", Map.of("vat_rate", 7));
		assertThat(badRate.status()).isEqualTo(422);
		assertThat(badRate.json().get("errors").has("vat_rate")).isTrue();

		Response badPrice = api.post("/api/orders", Map.of("customer_id", 2, "delivery_address_id", address(2),
			"order_date", "2026-11-27", "items", List.of(Map.of("finished_product_id", 2, "quantity", 1,
				"unit_price", -1))));
		assertThat(badPrice.status()).isEqualTo(422);

		assertThat(api.get("/api/invoices/999999").status()).isEqualTo(404);
		assertThat(api.post("/api/orders/999999/invoice", Map.of()).status()).isEqualTo(404);
		assertThat(api.as("operator1@olivamore.com").get("/api/invoices").status()).isEqualTo(403);
		assertThat(api.as("operator1@olivamore.com").post("/api/orders/1/invoice", Map.of()).status())
			.isEqualTo(403);
	}

	private long address(long customer) {
		return jdbc.queryForObject("select id from customer_addresses where customer_id = ? order by id limit 1",
			Long.class, customer);
	}

	private Response status(long order, String status) {
		return api.patch("/api/orders/" + order + "/status", Map.of("status", status));
	}

	private static void assertMoney(JsonNode node, String expected) {
		assertThat(node.decimalValue()).isEqualByComparingTo(new BigDecimal(expected));
	}

	private static void assertRule(Response r, String detail) {
		assertThat(r.status()).as(r.body()).isEqualTo(422);
		assertThat(r.json().get("detail").asString()).isEqualTo(detail);
	}

}
