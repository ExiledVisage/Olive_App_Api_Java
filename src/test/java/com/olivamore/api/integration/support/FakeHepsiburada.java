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
 * Testlerde Hepsiburada satici API'sinin yerine (ilan ve siparis sunucularinin ikisi de burada): ilanlar, odemesi
 * tamamlanmis siparis kalemleri, paketleme, stok yukleme ve yukleme sonucu. Basic auth, User-Agent ve yoldaki
 * magaza ID'si (ilanda {@code merchantid}, sipariste {@code merchantId}) denetlenir; liste uclari offset ve limit
 * olmadan 400 doner. Istekler kaydedilir.
 */
public final class FakeHepsiburada implements AutoCloseable {

	public static final String MERCHANT = "6f3c1b2a-merchant";
	public static final String USER = "olivamore_api";
	public static final String PASSWORD = "hbSecret123456";
	public static final String INTEGRATOR = "olivamore_dev";

	/** Kaydedilen istek. */
	public record Request(String method, String path, String body, Map<String, String> headers) {
	}

	/** Siparis kalemi. */
	public record Line(String lineId, String hbSku, String merchantSku, String name, int quantity) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpServer server;
	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
	private final List<Map<String, Object>> listings = new ArrayList<>();
	private final List<Map<String, Object>> lines = new ArrayList<>();
	private final Map<String, Map<String, Object>> uploads = new HashMap<>();
	private final Set<String> rejected = new HashSet<>();
	private final Set<String> cancelled = new HashSet<>();
	private int uploadSeq = 0;
	private int packageSeq = 0;

	public FakeHepsiburada() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/", this::handle);
		server.start();
	}

	/** Baglantinin "API adresi" (canli sunucular yerine). */
	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public List<Request> requests() {
		return List.copyOf(requests);
	}

	public FakeHepsiburada listing(String hbSku, String merchantSku, int stock) {
		Map<String, Object> l = new LinkedHashMap<>();
		l.put("listingId", "L-" + hbSku);
		l.put("uniqueIdentifier", "U-" + hbSku);
		l.put("hepsiburadaSku", hbSku);
		l.put("merchantSku", merchantSku);
		l.put("price", 249.9);
		l.put("availableStock", stock);
		l.put("dispatchTime", 2);
		l.put("isSalable", true);
		l.put("isFulfilledByHB", false);
		listings.add(l);
		return this;
	}

	/** Stogu Hepsiburada'nin tuttugu ilan (alinmamali). */
	public FakeHepsiburada fulfilledByHb(String hbSku, String merchantSku) {
		listing(hbSku, merchantSku, 50);
		listings.getLast().put("isFulfilledByHB", true);
		return this;
	}

	/** Odemesi tamamlanmis siparis; her kalem listede ayri satir, alici ve adres her satirda. */
	public FakeHepsiburada order(String orderNumber, String orderDate, String name, String email, String phone,
		String city, String town, String district, String address, List<Line> items) {
		for (Line item : items) {
			Map<String, Object> l = new LinkedHashMap<>();
			l.put("id", item.lineId());
			l.put("orderId", "O-" + orderNumber);
			l.put("orderNumber", orderNumber);
			l.put("status", "Open");
			l.put("orderDate", orderDate);
			l.put("sku", item.hbSku());
			l.put("merchantSKU", item.merchantSku());
			l.put("name", item.name());
			l.put("quantity", item.quantity());
			l.put("customerName", name);
			l.put("unitPrice", Map.of("amount", 249.9, "currency", "TRY"));
			l.put("shippingAddress", Map.of("addressId", "A-" + orderNumber, "name", name, "email", email,
				"phoneNumber", phone, "countryCode", "TR", "city", city, "town", town, "district", district,
				"address", address));
			lines.add(l);
		}
		return this;
	}

	/** Bu SKU'nun stok guncellemesi yukleme sonucunda hatali doner. */
	public FakeHepsiburada rejectStockFor(String hbSku) {
		rejected.add(hbSku);
		return this;
	}

	/** Musteri siparisi Hepsiburada'da iptal etti: kalemleri paketlenemez. */
	public FakeHepsiburada cancel(String orderNumber) {
		cancelled.add(orderNumber);
		return this;
	}

	public FakeHepsiburada restore(String orderNumber) {
		cancelled.remove(orderNumber);
		return this;
	}

	/** Paketlenen siparisin paket numarasi; paketlenmediyse null. */
	public String packageOf(String orderNumber) {
		return lines.stream().filter(l -> orderNumber.equals(l.get("orderNumber")))
			.map(l -> (String) l.get("packageNumber")).filter(p -> p != null).findFirst().orElse(null);
	}

	public Integer stockOf(String hbSku) {
		return listings.stream().filter(l -> hbSku.equals(l.get("hepsiburadaSku"))).findFirst()
			.map(l -> (Integer) l.get("availableStock")).orElse(null);
	}

	@Override
	public void close() {
		server.stop(0);
	}

	@SuppressWarnings("unchecked")
	private void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		URI uri = exchange.getRequestURI();
		String path = uri.getPath();
		Map<String, String> headers = new HashMap<>();
		exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
		requests.add(new Request(exchange.getRequestMethod(), path + (uri.getQuery() == null ? "" : "?"
			+ uri.getQuery()), body, headers));
		String expected = "Basic " + Base64.getEncoder().encodeToString((USER + ":" + PASSWORD)
			.getBytes(StandardCharsets.UTF_8));
		if (!expected.equals(headers.get("authorization")) || !INTEGRATOR.equals(headers.get("user-agent"))) {
			send(exchange, 401, Map.of("message", "Unauthorized"));
			return;
		}
		Map<String, String> query = query(uri.getRawQuery());
		String method = exchange.getRequestMethod();
		String listingBase = "/listings/merchantid/" + MERCHANT;
		if ("GET".equals(method) && path.equals(listingBase)) {
			page(exchange, listings, query, "listings");
		} else if ("POST".equals(method) && path.equals(listingBase + "/stock-uploads")) {
			List<Map<String, Object>> errors = new ArrayList<>();
			int no = 0;
			for (Object entry : JSON.readValue(body, List.class)) {
				Map<String, Object> item = (Map<String, Object>) entry;
				String hbSku = String.valueOf(item.get("hepsiburadaSku"));
				if (rejected.contains(hbSku)) {
					errors.add(Map.of("elementNo", no, "hepsiburadaSku", hbSku, "errors",
						List.of("Ürün satışa kapalı.")));
				} else {
					listings.stream().filter(l -> hbSku.equals(l.get("hepsiburadaSku")))
						.forEach(l -> l.put("availableStock", ((Number) item.get("availableStock")).intValue()));
				}
				no++;
			}
			String id = "upload-" + (++uploadSeq);
			uploads.put(id, Map.of("id", id, "status", "Done", "total", no, "errors", errors));
			send(exchange, 200, Map.of("id", id));
		} else if ("GET".equals(method) && path.startsWith(listingBase + "/stock-uploads/id/")) {
			String id = path.substring(path.lastIndexOf('/') + 1);
			send(exchange, uploads.containsKey(id) ? 200 : 404, uploads.getOrDefault(id, Map.of()));
		} else if ("GET".equals(method) && path.equals("/orders/merchantId/" + MERCHANT)) {
			if (!query.containsKey("offset") || !query.containsKey("limit")) {
				send(exchange, 400, Map.of("code", 1001, "message",
					"Bad Request limit and offset parameters are required"));
				return;
			}
			page(exchange, lines.stream().filter(l -> l.get("packageNumber") == null).toList(), query, "items");
		} else if ("POST".equals(method) && path.equals("/packages/merchantId/" + MERCHANT)) {
			createPackage(exchange, JSON.readValue(body, Map.class));
		} else {
			send(exchange, 404, Map.of("message", "Not found"));
		}
	}

	/** Kalemler acik ve adetler tam olmali; iptal edilen siparis paketlenemez. */
	@SuppressWarnings("unchecked")
	private void createPackage(HttpExchange exchange, Map<String, Object> request) throws IOException {
		List<Object> items = request.get("lineItemRequests") instanceof List<?> l ? (List<Object>) l : List.of();
		if (items.isEmpty()) {
			send(exchange, 400, Map.of("message", "Line items empty or null"));
			return;
		}
		List<Map<String, Object>> picked = new ArrayList<>();
		for (Object entry : items) {
			Map<String, Object> item = (Map<String, Object>) entry;
			Map<String, Object> line = lines.stream().filter(l -> l.get("id").equals(item.get("id"))
				&& l.get("packageNumber") == null).findFirst().orElse(null);
			if (line == null || !line.get("quantity").equals(item.get("quantity"))) {
				send(exchange, 400, Map.of("message", "Kalem paketlenebilir değil: " + item.get("id")));
				return;
			}
			if (cancelled.contains(line.get("orderNumber"))) {
				send(exchange, 400, Map.of("message", "Sipariş iptal edilmiş."));
				return;
			}
			picked.add(line);
		}
		String number = "PKG-" + (++packageSeq);
		picked.forEach(l -> l.put("packageNumber", number));
		send(exchange, 201, Map.of("packageNumber", number, "barcode", "HB" + number));
	}

	private static void page(HttpExchange exchange, List<Map<String, Object>> all, Map<String, String> query,
		String field) throws IOException {
		if (!query.containsKey("offset") || !query.containsKey("limit")) {
			send(exchange, 400, Map.of("message", "offset and limit are required"));
			return;
		}
		int offset = Integer.parseInt(query.get("offset"));
		int limit = Integer.parseInt(query.get("limit"));
		int from = Math.min(all.size(), offset);
		int to = Math.min(all.size(), from + limit);
		send(exchange, 200, Map.of("totalCount", all.size(), "offset", offset, "limit", limit, field,
			all.subList(from, to)));
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
