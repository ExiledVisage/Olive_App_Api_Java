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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Testlerde Parasut API v4'un yerine: OAuth2 sifre akisi ({@code /oauth/token}), {@code /v4/{firma}} altinda cari,
 * urun, satis faturasi, e-fatura posta kutusu, e-fatura ve e-arsiv resmilestirme (is olarak), is durumu, faturanin
 * resmi belgesi ve iptal. Token ve firma denetlenir; govdeler JSON:API. Istekler kaydedilir.
 */
public final class FakeParasut implements AutoCloseable {

	public static final String COMPANY = "115";
	public static final String CLIENT_ID = "olivamore-app";
	public static final String CLIENT_SECRET = "parasutSecret123";
	public static final String USERNAME = "muhasebe@olivamore.com";
	public static final String PASSWORD = "parasutPass123";
	private static final String TOKEN = "fake-parasut-token";

	/** Kaydedilen istek. */
	public record Request(String method, String path, String body) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpServer server;
	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
	private final Map<String, Map<String, Object>> contacts = new LinkedHashMap<>();
	private final Map<String, Map<String, Object>> products = new LinkedHashMap<>();
	private final Map<String, Map<String, Object>> invoices = new LinkedHashMap<>();
	private final Map<String, Map<String, Object>> jobs = new HashMap<>();
	private final Map<String, String> inboxes = new HashMap<>();
	private int seq = 1000;
	private String failInvoices;
	private String rejectFormalization;
	private int runningPolls;

	public FakeParasut() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/", this::handle);
		server.start();
	}

	/** Baglantinin "API adresi" (canli sunucu yerine). */
	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public List<Request> requests() {
		return List.copyOf(requests);
	}

	/** Parasut'te onceden var olan cari. */
	public FakeParasut contact(String id, String name, String taxNumber) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("name", name);
		attributes.put("tax_number", taxNumber);
		contacts.put(id, attributes);
		return this;
	}

	/** Bu vergi numarasi e-fatura mukellefi. */
	public FakeParasut eInvoiceUser(String taxNumber, String address) {
		inboxes.put(taxNumber, address);
		return this;
	}

	/** Satis faturasi olusturma bu gerekceyle reddedilir; null ise kabul edilir. */
	public FakeParasut failInvoices(String detail) {
		failInvoices = detail;
		return this;
	}

	/** Resmilestirme isi bu gerekceyle hatali biter; null ise basarili. */
	public FakeParasut rejectFormalization(String reason) {
		rejectFormalization = reason;
		return this;
	}

	/** Is ilk bu kadar sorguda "running" doner. */
	public FakeParasut runningPolls(int polls) {
		runningPolls = polls;
		return this;
	}

	public Map<String, Map<String, Object>> contacts() {
		return contacts;
	}

	public Map<String, Map<String, Object>> products() {
		return products;
	}

	/** Olusturulan satis faturalari: attributes, details, contact, e_document, cancelled. */
	public Map<String, Map<String, Object>> invoices() {
		return invoices;
	}

	@Override
	public void close() {
		server.stop(0);
	}

	@SuppressWarnings("unchecked")
	private synchronized void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		URI uri = exchange.getRequestURI();
		String path = uri.getPath();
		String method = exchange.getRequestMethod();
		Map<String, String> query = query(uri.getRawQuery());
		requests.add(new Request(method, path + (uri.getRawQuery() == null ? "" : "?"
			+ URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8)), body));
		if ("POST".equals(method) && path.equals("/oauth/token")) {
			Map<String, String> form = query(body);
			boolean ok = "password".equals(form.get("grant_type")) && CLIENT_ID.equals(form.get("client_id"))
				&& CLIENT_SECRET.equals(form.get("client_secret")) && USERNAME.equals(form.get("username"))
				&& PASSWORD.equals(form.get("password"));
			if (ok) {
				send(exchange, 200, Map.of("access_token", TOKEN, "token_type", "bearer", "expires_in", 7200,
					"refresh_token", "r"));
			} else {
				send(exchange, 401, Map.of("error", "invalid_grant", "error_description", "Hatalı giriş"));
			}
			return;
		}
		if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
			send(exchange, 401, Map.of("errors", List.of(Map.of("title", "Unauthorized"))));
			return;
		}
		String base = "/v4/" + COMPANY;
		if (!path.startsWith(base + "/")) {
			send(exchange, 404, errors("Firma bulunamadı."));
			return;
		}
		String resource = path.substring(base.length());
		Map<String, Object> data = body.isBlank() ? Map.of()
			: (Map<String, Object>) ((Map<String, Object>) JSON.readValue(body, Map.class)).get("data");
		if ("GET".equals(method) && resource.equals("/contacts")) {
			list(exchange, "contacts", contacts, query, "tax_number", "email");
		} else if ("POST".equals(method) && resource.equals("/contacts")) {
			created(exchange, "contacts", contacts, (Map<String, Object>) data.get("attributes"));
		} else if ("GET".equals(method) && resource.equals("/products")) {
			list(exchange, "products", products, query, "code", "name");
		} else if ("POST".equals(method) && resource.equals("/products")) {
			created(exchange, "products", products, (Map<String, Object>) data.get("attributes"));
		} else if ("POST".equals(method) && resource.equals("/sales_invoices")) {
			createInvoice(exchange, data);
		} else if ("GET".equals(method) && resource.startsWith("/sales_invoices/")) {
			showInvoice(exchange, resource.substring("/sales_invoices/".length()));
		} else if ("DELETE".equals(method) && resource.matches("/sales_invoices/\\d+/cancel")) {
			Map<String, Object> invoice = invoices.get(resource.split("/")[2]);
			if (invoice == null) {
				send(exchange, 404, errors("Fatura bulunamadı."));
			} else if (invoice.get("e_document") != null) {
				send(exchange, 422, errors("Resmileşmiş fatura iptal edilemez."));
			} else {
				invoice.put("cancelled", true);
				send(exchange, 200, Map.of("data", Map.of("id", resource.split("/")[2], "type", "sales_invoices")));
			}
		} else if ("GET".equals(method) && resource.equals("/e_invoice_inboxes")) {
			String vkn = query.get("filter[vkn]");
			List<Object> found = inboxes.containsKey(vkn) ? List.of(Map.of("id", "inbox-" + vkn, "type",
				"e_invoice_inboxes", "attributes", Map.of("vkn", vkn, "e_invoice_address", inboxes.get(vkn))))
				: List.of();
			send(exchange, 200, Map.of("data", found));
		} else if ("POST".equals(method) && (resource.equals("/e_invoices") || resource.equals("/e_archives"))) {
			formalize(exchange, resource.substring(1), data);
		} else if ("GET".equals(method) && resource.startsWith("/trackable_jobs/")) {
			job(exchange, resource.substring("/trackable_jobs/".length()));
		} else {
			send(exchange, 404, errors("Not found"));
		}
	}

	@SuppressWarnings("unchecked")
	private void createInvoice(HttpExchange exchange, Map<String, Object> data) throws IOException {
		if (failInvoices != null) {
			send(exchange, 422, errors(failInvoices));
			return;
		}
		Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
		String contactId = refId(relationships.get("contact"));
		if (!contacts.containsKey(contactId)) {
			send(exchange, 422, errors("Cari bulunamadı."));
			return;
		}
		List<Object> details = (List<Object>) ((Map<String, Object>) relationships.get("details")).get("data");
		for (Object detail : details) {
			String productId = refId(((Map<String, Object>) ((Map<String, Object>) detail).get("relationships"))
				.get("product"));
			if (!products.containsKey(productId)) {
				send(exchange, 422, errors("Ürün bulunamadı."));
				return;
			}
		}
		String id = String.valueOf(++seq);
		Map<String, Object> invoice = new LinkedHashMap<>();
		invoice.put("attributes", data.get("attributes"));
		invoice.put("details", details);
		invoice.put("contact", contactId);
		invoices.put(id, invoice);
		send(exchange, 201, Map.of("data", Map.of("id", id, "type", "sales_invoices", "attributes",
			data.get("attributes"))));
	}

	private void showInvoice(HttpExchange exchange, String id) throws IOException {
		Map<String, Object> invoice = invoices.get(id);
		if (invoice == null) {
			send(exchange, 404, errors("Fatura bulunamadı."));
			return;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> document = (Map<String, Object>) invoice.get("e_document");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("id", id);
		data.put("type", "sales_invoices");
		data.put("attributes", invoice.get("attributes"));
		Map<String, Object> response = new LinkedHashMap<>();
		if (document != null) {
			data.put("relationships", Map.of("active_e_document", Map.of("data", Map.of("id", document.get("id"),
				"type", document.get("type")))));
			response.put("included", List.of(Map.of("id", document.get("id"), "type", document.get("type"),
				"attributes", Map.of("invoice_number", document.get("number")))));
		}
		response.put("data", data);
		send(exchange, 200, response);
	}

	@SuppressWarnings("unchecked")
	private void formalize(HttpExchange exchange, String type, Map<String, Object> data) throws IOException {
		Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
		String invoiceId = refId(relationships.get("e_invoices".equals(type) ? "invoice" : "sales_invoice"));
		Map<String, Object> invoice = invoices.get(invoiceId);
		if (invoice == null) {
			send(exchange, 422, errors("Fatura bulunamadı."));
			return;
		}
		String jobId = "job-" + (++seq);
		Map<String, Object> job = new HashMap<>();
		job.put("invoice", invoiceId);
		job.put("type", type);
		job.put("attributes", data.get("attributes"));
		job.put("polls", 0);
		jobs.put(jobId, job);
		send(exchange, 202, Map.of("data", Map.of("id", jobId, "type", "trackable_jobs", "attributes",
			Map.of("status", "pending"))));
	}

	private void job(HttpExchange exchange, String jobId) throws IOException {
		Map<String, Object> job = jobs.get(jobId);
		if (job == null) {
			send(exchange, 404, errors("İş bulunamadı."));
			return;
		}
		int polls = (int) job.get("polls") + 1;
		job.put("polls", polls);
		Map<String, Object> attributes = new LinkedHashMap<>();
		if (polls <= runningPolls) {
			attributes.put("status", "running");
		} else if (rejectFormalization != null) {
			attributes.put("status", "error");
			attributes.put("errors", List.of(rejectFormalization));
		} else {
			attributes.put("status", "done");
			Map<String, Object> invoice = invoices.get((String) job.get("invoice"));
			if (invoice.get("e_document") == null) {
				String prefix = "e_invoices".equals(job.get("type")) ? "EFT" : "EAR";
				invoice.put("e_document", Map.of("id", "doc-" + (++seq), "type", job.get("type"), "number",
					prefix + "2026" + String.format("%09d", seq), "attributes", job.get("attributes")));
			}
		}
		send(exchange, 200, Map.of("data", Map.of("id", jobId, "type", "trackable_jobs", "attributes", attributes)));
	}

	private void list(HttpExchange exchange, String type, Map<String, Map<String, Object>> store,
		Map<String, String> query, String... filters) throws IOException {
		List<Object> data = new ArrayList<>();
		store.forEach((id, attributes) -> {
			for (String filter : filters) {
				String value = query.get("filter[" + filter + "]");
				if (value != null && !value.equals(attributes.get(filter))) {
					return;
				}
			}
			data.add(Map.of("id", id, "type", type, "attributes", attributes));
		});
		send(exchange, 200, Map.of("data", data, "meta", Map.of("total_count", data.size())));
	}

	private void created(HttpExchange exchange, String type, Map<String, Map<String, Object>> store,
		Map<String, Object> attributes) throws IOException {
		if (attributes == null || attributes.get("name") == null) {
			send(exchange, 422, errors("Ad boş olamaz."));
			return;
		}
		String id = String.valueOf(++seq);
		store.put(id, new LinkedHashMap<>(attributes));
		send(exchange, 201, Map.of("data", Map.of("id", id, "type", type, "attributes", attributes)));
	}

	@SuppressWarnings("unchecked")
	private static String refId(Object relationship) {
		if (!(relationship instanceof Map<?, ?> r) || !(r.get("data") instanceof Map<?, ?> d)) {
			return null;
		}
		return String.valueOf(((Map<String, Object>) d).get("id"));
	}

	private static Map<String, Object> errors(String detail) {
		return Map.of("errors", List.of(Map.of("title", "Unprocessable", "detail", detail)));
	}

	private static Map<String, String> query(String raw) {
		Map<String, String> result = new HashMap<>();
		if (raw == null || raw.isBlank()) {
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
