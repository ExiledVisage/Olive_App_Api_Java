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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Testlerde Trendyol satici API'sinin yerine ({@code /integration}): onayli urunler, V2 siparis paketleri, stok-fiyat
 * gonderimi ve kuyruktaki isin sonucu, Trendyol'un belgelenmis bicimiyle. Basic auth, {@code storeFrontCode} basligi
 * ve yoldaki satici ID denetlenir; istekler kaydedilir.
 */
public final class FakeTrendyol implements AutoCloseable {

	public static final String SELLER = "123456";
	public static final String KEY = "tyKey1234567890";
	public static final String SECRET = "tySecret0987654321";

	/** Kaydedilen istek. */
	public record Request(String method, String path, String body, Map<String, String> headers) {
	}

	/** Paket kalemi. */
	public record Line(long lineId, String barcode, String productName, int quantity) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpServer server;
	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
	private final List<Map<String, Object>> products = new ArrayList<>();
	private final List<Map<String, Object>> packages = new ArrayList<>();
	private final Map<String, Map<String, Object>> batches = new HashMap<>();
	private final Set<String> rejected = new HashSet<>();
	private int batchSeq = 0;

	public FakeTrendyol() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/integration/", this::handle);
		server.start();
	}

	/** Baglantinin "API adresi" (canli yerine). */
	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/integration";
	}

	public List<Request> requests() {
		return List.copyOf(requests);
	}

	public FakeTrendyol product(String barcode, String stockCode, String title, int quantity) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("id", "c" + barcode);
		p.put("barcode", barcode);
		p.put("stockCode", stockCode);
		p.put("productMainId", "M-" + stockCode);
		p.put("title", title);
		p.put("quantity", quantity);
		p.put("salePrice", 249.9);
		p.put("listPrice", 299.9);
		p.put("approved", true);
		p.put("archived", false);
		products.add(p);
		return this;
	}

	/** Arsivlenmis urun (alinmamali). */
	public FakeTrendyol archived(String barcode, String title) {
		product(barcode, "ARC", title, 0);
		products.getLast().put("archived", true);
		return this;
	}

	/**
	 * Siparis paketi. {@code orderDateMillis} Trendyol'daki gibi GMT+3 duvar saatinin milisaniyesi.
	 */
	public FakeTrendyol shipmentPackage(long packageId, String orderNumber, String status, long orderDateMillis,
		long lastModifiedMillis, String firstName, String lastName, String email, String phone, String city,
		String district, String fullAddress, List<Line> lines) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("id", packageId);
		p.put("shipmentPackageId", packageId);
		p.put("orderNumber", orderNumber);
		p.put("status", status);
		p.put("shipmentPackageStatus", status);
		p.put("orderDate", orderDateMillis);
		p.put("lastModifiedDate", lastModifiedMillis);
		p.put("customerFirstName", firstName);
		p.put("customerLastName", lastName);
		p.put("customerEmail", email);
		p.put("shipmentAddress", Map.of("id", packageId * 10, "firstName", firstName, "lastName", lastName,
			"address1", fullAddress, "city", city, "district", district, "postalCode", "34200", "countryCode", "TR",
			"phone", phone, "fullAddress", fullAddress + " " + district + "/" + city));
		p.put("lines", lines.stream().map(l -> Map.of("id", l.lineId(), "lineId", l.lineId(), "barcode",
			l.barcode(), "productName", l.productName(), "quantity", l.quantity(), "merchantSku", "SKU-" + l.barcode(),
			"price", 249.9)).toList());
		packages.add(p);
		return this;
	}

	/** Bu barkodun stok guncellemesi kuyruk sonucunda FAILED doner. */
	public FakeTrendyol rejectStockFor(String barcode) {
		rejected.add(barcode);
		return this;
	}

	public Integer stockOf(String barcode) {
		return products.stream().filter(p -> barcode.equals(p.get("barcode"))).findFirst()
			.map(p -> (Integer) p.get("quantity")).orElse(null);
	}

	@Override
	public void close() {
		server.stop(0);
	}

	@SuppressWarnings("unchecked")
	private void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		URI uri = exchange.getRequestURI();
		String path = uri.getPath().substring("/integration".length());
		Map<String, String> headers = new HashMap<>();
		exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
		requests.add(new Request(exchange.getRequestMethod(), path + (uri.getQuery() == null ? "" : "?"
			+ uri.getQuery()), body, headers));
		String expected = "Basic " + Base64.getEncoder().encodeToString((KEY + ":" + SECRET)
			.getBytes(StandardCharsets.UTF_8));
		if (!expected.equals(headers.get("authorization")) || !path.contains("/sellers/" + SELLER + "/")) {
			send(exchange, 401, Map.of("errors", List.of(Map.of("key", "ClientApiAuthenticationException",
				"message", "One of supplierID, API Key, or API Secret Key is missing or incorrect."))));
			return;
		}
		if (!"TR".equals(headers.get("storefrontcode"))) {
			send(exchange, 400, Map.of("errors", List.of(Map.of("message", "storeFrontCode is required"))));
			return;
		}
		Map<String, String> query = query(uri.getRawQuery());
		String method = exchange.getRequestMethod();
		if ("GET".equals(method) && path.endsWith("/products") && path.startsWith("/product/")) {
			page(exchange, products, query);
		} else if ("GET".equals(method) && path.endsWith("/v2/orders")) {
			String status = query.get("status");
			long start = Long.parseLong(query.getOrDefault("startDate", "0"));
			long end = Long.parseLong(query.getOrDefault("endDate", String.valueOf(Long.MAX_VALUE)));
			page(exchange, packages.stream()
				.filter(p -> status == null || status.equals(p.get("status")))
				.filter(p -> {
					long modified = ((Number) p.get("lastModifiedDate")).longValue();
					return modified >= start && modified <= end;
				}).toList(), query);
		} else if ("POST".equals(method) && path.endsWith("/products/price-and-inventory")) {
			Map<String, Object> request = JSON.readValue(body, Map.class);
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object entry : (List<Object>) request.get("items")) {
				Map<String, Object> item = (Map<String, Object>) entry;
				String barcode = String.valueOf(item.get("barcode"));
				boolean failed = rejected.contains(barcode);
				if (!failed) {
					products.stream().filter(p -> barcode.equals(p.get("barcode")))
						.forEach(p -> p.put("quantity", ((Number) item.get("quantity")).intValue()));
				}
				items.add(Map.of("requestItem", item, "status", failed ? "FAILED" : "SUCCESS", "failureReasons",
					failed ? List.of("Ürün satışa kapalı.") : List.of()));
			}
			String id = "batch-" + (++batchSeq);
			batches.put(id, Map.of("batchRequestId", id, "status", "COMPLETED", "itemCount", items.size(),
				"failedItemCount", items.stream().filter(i -> "FAILED".equals(i.get("status"))).count(), "items",
				items));
			send(exchange, 200, Map.of("batchRequestId", id));
		} else if ("GET".equals(method) && path.contains("/products/batch-requests/")) {
			String id = path.substring(path.lastIndexOf('/') + 1);
			send(exchange, batches.containsKey(id) ? 200 : 404, batches.getOrDefault(id, Map.of()));
		} else {
			send(exchange, 404, Map.of("errors", List.of(Map.of("message", "Not found"))));
		}
	}

	private static void page(HttpExchange exchange, List<Map<String, Object>> all, Map<String, String> query)
		throws IOException {
		int size = Integer.parseInt(query.getOrDefault("size", "50"));
		int page = Integer.parseInt(query.getOrDefault("page", "0"));
		int from = Math.min(all.size(), page * size);
		int to = Math.min(all.size(), from + size);
		int pages = Math.max(1, (int) Math.ceil(all.size() / (double) size));
		send(exchange, 200, Map.of("totalElements", all.size(), "totalPages", pages, "page", page, "size", size,
			"content", all.subList(from, to)));
	}

	private static Map<String, String> query(String raw) {
		Map<String, String> result = new HashMap<>();
		if (raw == null) {
			return result;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			result.put(URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8),
				eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
		}
		return result;
	}

	private static void send(HttpExchange exchange, int status, Object body) throws IOException {
		byte[] bytes = JSON.writeValueAsBytes(body);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

}
