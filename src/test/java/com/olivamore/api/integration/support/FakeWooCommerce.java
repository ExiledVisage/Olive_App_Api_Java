package com.olivamore.api.integration.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Testlerde gercek WooCommerce sitesinin yerine: {@code /wp-json/wc/v3} altindaki uclari WooCommerce'in belgelenmis
 * cevap bicimiyle taklit eder, Basic auth'u denetler, gelen istekleri kaydeder. Canli siteye ihtiyac birakmaz.
 */
public final class FakeWooCommerce implements AutoCloseable {

	public static final String KEY = "ck_test_1234567890abcdef";
	public static final String SECRET = "cs_test_fedcba0987654321";

	/** Kaydedilen istek: yontem, yol (sorgu dahil), govde. */
	public record Request(String method, String path, String body) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpServer server;
	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
	private final List<Map<String, Object>> products = new ArrayList<>();
	private final Map<Long, List<Map<String, Object>>> variations = new HashMap<>();
	private final List<Map<String, Object>> orders = new ArrayList<>();
	private final java.util.Set<Long> rejected = new java.util.HashSet<>();
	private String currency = "TRY";

	public FakeWooCommerce() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/wp-json/wc/v3/", this::handle);
		server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public List<Request> requests() {
		return List.copyOf(requests);
	}

	public FakeWooCommerce currency(String value) {
		currency = value;
		return this;
	}

	/** Basit urun (varyantsiz); barkod WooCommerce 9.2+ cekirdek alaninda ({@code global_unique_id}). */
	public FakeWooCommerce product(long id, String name, String sku, String barcode, Integer stock) {
		Map<String, Object> product = item(id, name, sku, stock);
		product.put("type", "simple");
		product.put("global_unique_id", barcode == null ? "" : barcode);
		products.add(product);
		return this;
	}

	/** Eski sitedeki gibi barkodu eklenti meta alaninda ({@code _ean}) tutan urun. */
	public FakeWooCommerce legacyProduct(long id, String name, String sku, String barcode, Integer stock) {
		Map<String, Object> product = item(id, name, sku, stock);
		product.put("type", "simple");
		product.put("meta_data", List.of(Map.of("id", id * 10, "key", "_ean", "value", barcode)));
		products.add(product);
		return this;
	}

	/** Varyantli urun; varyantlar {@link #variation} ile. */
	public FakeWooCommerce variable(long id, String name) {
		Map<String, Object> product = item(id, name, "", null);
		product.put("type", "variable");
		products.add(product);
		variations.put(id, new ArrayList<>());
		return this;
	}

	public FakeWooCommerce variation(long parentId, long id, String option, String sku, String barcode,
		Integer stock) {
		Map<String, Object> variation = item(id, null, sku, stock);
		variation.remove("name");
		variation.put("attributes", List.of(Map.of("id", 1, "name", "Hacim", "option", option)));
		variation.put("meta_data", barcode == null ? List.of() : List.of(Map.of("id", id * 10, "key", "_ean",
			"value", barcode)));
		variations.get(parentId).add(variation);
		return this;
	}

	/** Siteden kaldir (katalogdan dusmesi icin). */
	public FakeWooCommerce remove(long id) {
		products.removeIf(p -> ((Number) p.get("id")).longValue() == id);
		return this;
	}

	/** Siparis kalemi: urun, varyant (yoksa 0), sitedeki ad, adet. */
	public record Line(long productId, long variationId, String name, int quantity) {
	}

	/**
	 * WooCommerce siparisi; tarihler GMT. Teslimat adresi {@code shipping}'te, e-posta ve telefon {@code billing}'de.
	 *
	 * @param modifiedGmt "2026-11-27T09:00:00"; {@code modified_after} suzgeci buna bakar
	 */
	public FakeWooCommerce order(long id, String number, String status, String modifiedGmt, String firstName,
		String lastName, String email, String phone, String city, String address, List<Line> lines) {
		Map<String, Object> order = new LinkedHashMap<>();
		order.put("id", id);
		order.put("number", number);
		order.put("status", status);
		order.put("currency", currency);
		order.put("date_created_gmt", modifiedGmt);
		order.put("date_modified_gmt", modifiedGmt);
		order.put("customer_note", "Kapıya bırakın");
		order.put("billing", fields("first_name", firstName, "last_name", lastName, "company", "", "address_1", "",
			"address_2", "", "city", city, "state", "TR35", "postcode", "", "country", "TR", "email", email,
			"phone", phone));
		order.put("shipping", fields("first_name", firstName, "last_name", lastName, "company", "", "address_1",
			address, "address_2", "D:3", "city", city, "state", "TR35", "postcode", "35000", "country", "TR"));
		order.put("line_items", lines.stream().map(l -> Map.of("id", l.productId() * 100, "name", l.name(),
			"product_id", l.productId(), "variation_id", l.variationId(), "quantity", l.quantity(), "sku", "",
			"total", "100.00")).toList());
		orders.add(order);
		return this;
	}

	/** Anahtar, deger, anahtar, deger... (Map.of 10 ciftle sinirli). */
	private static Map<String, Object> fields(String... keyValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	private static Map<String, Object> item(long id, String name, String sku, Integer stock) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", id);
		item.put("name", name);
		item.put("status", "publish");
		item.put("sku", sku == null ? "" : sku);
		item.put("manage_stock", stock != null);
		item.put("stock_quantity", stock);
		item.put("global_unique_id", "");
		item.put("meta_data", List.of());
		return item;
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		URI uri = exchange.getRequestURI();
		String path = uri.getPath().substring("/wp-json/wc/v3".length());
		requests.add(new Request(exchange.getRequestMethod(), path + (uri.getQuery() == null ? "" : "?"
			+ uri.getQuery()), body));
		if (!authorized(exchange)) {
			send(exchange, 401, Map.of("code", "woocommerce_rest_cannot_view", "message",
				"Sorry, you cannot list resources.", "data", Map.of("status", 401)), Map.of());
			return;
		}
		Map<String, String> query = query(uri.getRawQuery());
		if ("GET".equals(exchange.getRequestMethod()) && path.equals("/products")) {
			page(exchange, products, query);
			return;
		}
		if ("GET".equals(exchange.getRequestMethod()) && path.matches("/products/[0-9]+/variations")) {
			long parent = Long.parseLong(path.split("/")[2]);
			page(exchange, variations.getOrDefault(parent, List.of()), query);
			return;
		}
		if ("POST".equals(exchange.getRequestMethod()) && path.equals("/products/batch")) {
			batch(exchange, products, body);
			return;
		}
		if ("POST".equals(exchange.getRequestMethod()) && path.matches("/products/[0-9]+/variations/batch")) {
			batch(exchange, variations.getOrDefault(Long.parseLong(path.split("/")[2]), List.of()), body);
			return;
		}
		if ("GET".equals(exchange.getRequestMethod()) && path.equals("/orders")) {
			String status = query.get("status");
			String after = query.get("modified_after");
			page(exchange, orders.stream()
				.filter(o -> status == null || status.equals(o.get("status")))
				.filter(o -> after == null || String.valueOf(o.get("date_modified_gmt")).compareTo(after) > 0)
				.toList(), query);
			return;
		}
		if ("GET".equals(exchange.getRequestMethod()) && path.equals("/settings/general/woocommerce_currency")) {
			send(exchange, 200, Map.of("id", "woocommerce_currency", "label", "Currency", "value", currency),
				Map.of());
			return;
		}
		send(exchange, 404, Map.of("code", "rest_no_route", "message", "No route was found matching the URL.",
			"data", Map.of("status", 404)), Map.of());
	}

	/** Sitedeki urun ya da varyantin stogu (stok gonderimini dogrulamak icin). */
	public Integer stockOf(long id) {
		List<Map<String, Object>> all = new ArrayList<>(products);
		variations.values().forEach(all::addAll);
		return all.stream().filter(p -> ((Number) p.get("id")).longValue() == id).findFirst()
			.map(p -> (Integer) p.get("stock_quantity")).orElse(null);
	}

	/** Sitede stogu elle degistir (katalog yenilenince bize gelir). */
	public FakeWooCommerce setStock(long id, int quantity) {
		List<Map<String, Object>> all = new ArrayList<>(products);
		variations.values().forEach(all::addAll);
		all.stream().filter(p -> ((Number) p.get("id")).longValue() == id).forEach(p -> {
			p.put("manage_stock", true);
			p.put("stock_quantity", quantity);
		});
		return this;
	}

	/** Bu id'nin stok guncellemesi WooCommerce'in kalem hatasiyla reddedilir. */
	public FakeWooCommerce rejectStockFor(long id) {
		rejected.add(id);
		return this;
	}

	/** WooCommerce toplu guncelleme: {@code {"update": [{"id", "stock_quantity", ...}]}}. */
	@SuppressWarnings("unchecked")
	private void batch(HttpExchange exchange, List<Map<String, Object>> items, String body) throws IOException {
		Map<String, Object> request = JSON.readValue(body, Map.class);
		List<Object> updated = new ArrayList<>();
		for (Object entry : (List<Object>) request.getOrDefault("update", List.of())) {
			Map<String, Object> change = (Map<String, Object>) entry;
			long id = ((Number) change.get("id")).longValue();
			if (rejected.contains(id)) {
				updated.add(Map.of("id", id, "error", Map.of("code", "woocommerce_rest_invalid_id", "message",
					"Geçersiz ID.", "data", Map.of("status", 400))));
				continue;
			}
			items.stream().filter(p -> ((Number) p.get("id")).longValue() == id).findFirst().ifPresent(p -> {
				p.put("manage_stock", change.get("manage_stock"));
				p.put("stock_quantity", ((Number) change.get("stock_quantity")).intValue());
				updated.add(p);
			});
		}
		send(exchange, 200, Map.of("update", updated), Map.of());
	}

	/** WooCommerce sayfalamasi: {@code per_page}, {@code page}; toplam {@code X-WP-Total(Pages)} basliginda. */
	private static void page(HttpExchange exchange, List<Map<String, Object>> all, Map<String, String> query)
		throws IOException {
		int perPage = Integer.parseInt(query.getOrDefault("per_page", "10"));
		int page = Integer.parseInt(query.getOrDefault("page", "1"));
		int from = Math.min(all.size(), (page - 1) * perPage);
		int to = Math.min(all.size(), from + perPage);
		int pages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
		send(exchange, 200, all.subList(from, to), Map.of("X-WP-Total", String.valueOf(all.size()),
			"X-WP-TotalPages", String.valueOf(pages)));
	}

	private static boolean authorized(HttpExchange exchange) {
		String header = exchange.getRequestHeaders().getFirst("Authorization");
		String expected = "Basic " + Base64.getEncoder().encodeToString((KEY + ":" + SECRET)
			.getBytes(StandardCharsets.UTF_8));
		return expected.equals(header);
	}

	private static Map<String, String> query(String raw) {
		Map<String, String> result = new HashMap<>();
		if (raw == null) {
			return result;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			result.put(key, value);
		}
		return result;
	}

	private static void send(HttpExchange exchange, int status, Object body, Map<String, String> headers)
		throws IOException {
		byte[] bytes = JSON.writeValueAsBytes(body);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
		headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

}
