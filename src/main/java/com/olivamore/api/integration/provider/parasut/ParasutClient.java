package com.olivamore.api.integration.provider.parasut;

import com.olivamore.api.integration.provider.AccountingClient;
import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.ExternalProduct;
import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.integration.provider.ProviderErrors;
import com.olivamore.api.integration.provider.ProviderException;
import com.olivamore.api.integration.provider.StockUpdate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Parasut API v4 istemcisi ({@code https://api.parasut.com}; docs/INVOICES.md). OAuth2 sifre akisiyla token alinir
 * ({@code POST /oauth/token}: uygulama id/gizli anahtari, kullanici e-postasi ve sifresi); isler
 * {@code /v4/{firma}/...} altinda, JSON:API bicimi ({@code data.type}, {@code attributes}, {@code relationships}).
 * Resmilestirme (e-fatura / e-arsiv) is ({@code trackable_jobs}) olarak yurur. Satis kanali degildir: urun, siparis
 * ve stok islerini desteklemez. Uclar resmi belgeden degil acik kaynak istemcilerden; gercek hesapla dogrulanacak.
 * Cevaplarin okunmasi {@link ParasutMapping}'de.
 */
public class ParasutClient implements ProviderClient, AccountingClient {

	static final String PLATFORM = "Paraşüt";
	public static final String LIVE_URL = "https://api.parasut.com";

	/** Parasut'te Turk lirasi. */
	static final String CURRENCY = "TRL";

	/** Sifre akisinda tarayicisiz uygulamalarin donus adresi. */
	private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";

	private final RestClient http;
	private final String companyId;
	private final String clientId;
	private final String clientSecret;
	private final String username;
	private final String password;
	private String token;

	public ParasutClient(String baseUrl, String companyId, String clientId, String clientSecret, String username,
		String password) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.http = RestClient.builder().requestFactory(factory).baseUrl(baseUrl == null ? LIVE_URL : baseUrl).build();
		this.companyId = companyId;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.username = username;
		this.password = password;
	}

	@Override
	public ConnectionCheck check() {
		try {
			Map<?, ?> page = get(api("/contacts"), Map.of("page[size]", "1"));
			Object total = ParasutMapping.meta(page).get("total_count");
			return new ConnectionCheck(true, "Paraşüt'e bağlanıldı" + (total == null ? "" : " · " + total + " cari")
				+ ".");
		} catch (ProviderException e) {
			return new ConnectionCheck(false, e.getMessage());
		}
	}

	@Override
	public List<ExternalProduct> products() {
		throw notASalesChannel();
	}

	@Override
	public List<ExternalOrder> orders(LocalDateTime changedAfter) {
		throw notASalesChannel();
	}

	@Override
	public List<StockUpdate.Result> pushStock(List<StockUpdate> updates) {
		throw notASalesChannel();
	}

	@Override
	public String contact(Contact contact) {
		if (contact.taxNumber() != null) {
			Optional<String> found = first("contacts", "filter[tax_number]", contact.taxNumber());
			if (found.isPresent()) {
				return found.get();
			}
		}
		if (contact.email() != null) {
			Optional<String> found = first("contacts", "filter[email]", contact.email());
			if (found.isPresent()) {
				return found.get();
			}
		}
		return id(post(api("/contacts"), ParasutMapping.contact(contact)));
	}

	@Override
	public String product(Product product) {
		if (product.code() != null) {
			Optional<String> found = first("products", "filter[code]", product.code());
			if (found.isPresent()) {
				return found.get();
			}
		}
		Optional<String> byName = first("products", "filter[name]", product.name());
		return byName.orElseGet(() -> id(post(api("/products"), ParasutMapping.product(product))));
	}

	@Override
	public String createSalesInvoice(SalesInvoice invoice) {
		return id(post(api("/sales_invoices"), ParasutMapping.salesInvoice(invoice)));
	}

	@Override
	public Optional<String> eInvoiceAddress(String taxNumber) {
		Map<?, ?> page = get(api("/e_invoice_inboxes"), Map.of("filter[vkn]", taxNumber));
		return ParasutMapping.list(page).stream()
			.map(inbox -> ParasutMapping.text(ParasutMapping.attributes(inbox).get("e_invoice_address")))
			.filter(address -> address != null).findFirst();
	}

	@Override
	public String formalizeEInvoice(String salesInvoiceId, String address) {
		return id(post(api("/e_invoices"), ParasutMapping.eInvoice(salesInvoiceId, address)));
	}

	@Override
	public String formalizeEArchive(String salesInvoiceId, InternetSale internetSale) {
		return id(post(api("/e_archives"), ParasutMapping.eArchive(salesInvoiceId, internetSale)));
	}

	@Override
	public Job job(String jobId) {
		return ParasutMapping.job(get(api("/trackable_jobs/" + jobId), Map.of()));
	}

	@Override
	public Optional<EDocument> eDocument(String salesInvoiceId) {
		return ParasutMapping.eDocument(get(api("/sales_invoices/" + salesInvoiceId),
			Map.of("include", "active_e_document")));
	}

	@Override
	public void cancelSalesInvoice(String salesInvoiceId) {
		call(() -> http.delete().uri(api("/sales_invoices/" + salesInvoiceId + "/cancel"))
			.headers(h -> h.setBearerAuth(token())).retrieve().toBodilessEntity());
	}

	private Optional<String> first(String resource, String filter, String value) {
		return ParasutMapping.list(get(api("/" + resource), Map.of(filter, value))).stream()
			.map(item -> ParasutMapping.text(item.get("id"))).filter(id -> id != null).findFirst();
	}

	private String api(String path) {
		return "/v4/" + companyId + path;
	}

	private Map<?, ?> get(String path, Map<String, String> query) {
		return call(() -> http.get().uri(b -> {
			b.path(path);
			query.forEach(b::queryParam);
			return b.build();
		}).headers(h -> h.setBearerAuth(token())).retrieve().body(Map.class));
	}

	private Map<?, ?> post(String path, Map<String, Object> body) {
		return call(() -> http.post().uri(path).headers(h -> h.setBearerAuth(token()))
			.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class));
	}

	private String id(Map<?, ?> response) {
		String id = ParasutMapping.text(ParasutMapping.data(response).get("id"));
		if (id == null) {
			throw new ProviderException(PLATFORM + " cevabında kayıt numarası yok.", null);
		}
		return id;
	}

	/** Sifre akisiyla bir kez alinir; istemci tek bir is icin kurulur (token iki saat gecerli). */
	private String token() {
		if (token == null) {
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("grant_type", "password");
			form.add("client_id", clientId);
			form.add("client_secret", clientSecret);
			form.add("username", username);
			form.add("password", password);
			form.add("redirect_uri", REDIRECT_URI);
			Map<?, ?> response = call(() -> http.post().uri("/oauth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class));
			token = response == null ? null : ParasutMapping.text(response.get("access_token"));
			if (token == null) {
				throw new ProviderException(PLATFORM + " giriş cevabında token yok.", null);
			}
		}
		return token;
	}

	private static <T> T call(Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	private static ProviderException notASalesChannel() {
		return new ProviderException(PLATFORM + " bir muhasebe bağlantısı; ürün, sipariş ve stok işi yok.", null);
	}

	/** JSON:API govdesi: {@code {"data": {"type": ..., "attributes": ..., "relationships": ...}}}. */
	static Map<String, Object> document(String type, Map<String, Object> attributes,
		Map<String, Object> relationships) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", type);
		data.put("attributes", attributes);
		if (relationships != null && !relationships.isEmpty()) {
			data.put("relationships", relationships);
		}
		return Map.of("data", data);
	}

	/** {@code {"data": {"id": ..., "type": ...}}} */
	static Map<String, Object> ref(String type, String id) {
		return Map.of("data", Map.of("id", id, "type", type));
	}

}
