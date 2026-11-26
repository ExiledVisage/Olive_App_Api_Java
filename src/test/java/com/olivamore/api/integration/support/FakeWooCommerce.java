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

	/** Basit urun (varyantsiz). */
	public FakeWooCommerce product(long id, String name, String sku, String barcode, Integer stock) {
		Map<String, Object> product = new LinkedHashMap<>();
		product.put("id", id);
		product.put("name", name);
		product.put("type", "simple");
		product.put("sku", sku == null ? "" : sku);
		product.put("manage_stock", stock != null);
		product.put("stock_quantity", stock);
		product.put("meta_data", barcode == null ? List.of() : List.of(Map.of("id", id * 10, "key", "_ean",
			"value", barcode)));
		products.add(product);
		return this;
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
			int perPage = Integer.parseInt(query.getOrDefault("per_page", "10"));
			int page = Integer.parseInt(query.getOrDefault("page", "1"));
			int from = Math.min(products.size(), (page - 1) * perPage);
			int to = Math.min(products.size(), from + perPage);
			int pages = Math.max(1, (int) Math.ceil(products.size() / (double) perPage));
			send(exchange, 200, products.subList(from, to), Map.of("X-WP-Total", String.valueOf(products.size()),
				"X-WP-TotalPages", String.valueOf(pages)));
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
