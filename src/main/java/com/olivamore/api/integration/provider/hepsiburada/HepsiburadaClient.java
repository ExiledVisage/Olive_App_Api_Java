package com.olivamore.api.integration.provider.hepsiburada;

import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.ExternalProduct;
import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.integration.provider.ProviderErrors;
import com.olivamore.api.integration.provider.ProviderException;
import com.olivamore.api.integration.provider.StockUpdate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hepsiburada satici API istemcisi. Hepsiburada isleri ayri sunuculara boler: ilan ve stok
 * {@code listing-external}, siparis ve paket {@code oms-external}. API kullanici adi ve sifresiyle Basic auth,
 * User-Agent entegrator adi; magaza (merchant) ID yolda. Yoldaki yazim sunucuya gore degisir: ilanda
 * {@code merchantid}, sipariste {@code merchantId} (ikisi de Hepsiburada'nin kabul ettigi bicim).
 * Baglantida API adresi verilirse (test) butun isler o adrese gider. Cevaplarin okunmasi {@link HepsiburadaMapping}'de.
 */
public class HepsiburadaClient implements ProviderClient {

	static final String PLATFORM = "Hepsiburada";
	public static final String LISTING_URL = "https://listing-external.hepsiburada.com";
	public static final String OMS_URL = "https://oms-external.hepsiburada.com";

	private static final String LISTINGS = "/listings/merchantid/{merchant}?offset={offset}&limit={limit}";
	private static final String STOCK = "/listings/merchantid/{merchant}/stock-uploads";
	private static final String STOCK_RESULT = "/listings/merchantid/{merchant}/stock-uploads/id/{id}";
	private static final String ORDERS = "/orders/merchantId/{merchant}?offset={offset}&limit={limit}";
	private static final String PACKAGES = "/packages/merchantId/{merchant}";

	/** Ilan sayfasinda kayit; siparis listesinde Hepsiburada en fazla 100 kabul eder. */
	private static final int LISTING_PAGE = 500;
	private static final int ORDER_PAGE = 100;
	private static final int MAX_PAGES = 100;

	/** Stok gonderiminde istek basina en fazla 1000 kalem. */
	private static final int STOCK_BATCH = 1000;

	/** Kuyruga alinan stok isinin sonucu icin bekleme. */
	private static final int RESULT_ATTEMPTS = 5;
	private static final Duration RESULT_WAIT = Duration.ofSeconds(2);

	private final RestClient listing;
	private final RestClient oms;
	private final String merchantId;

	public HepsiburadaClient(String baseUrl, String merchantId, String username, String password,
		String integrator) {
		this.merchantId = merchantId;
		this.listing = http(baseUrl == null ? LISTING_URL : baseUrl, username, password, integrator);
		this.oms = http(baseUrl == null ? OMS_URL : baseUrl, username, password, integrator);
	}

	private static RestClient http(String baseUrl, String username, String password, String integrator) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(30));
		return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).defaultHeaders(h -> {
			h.setBasicAuth(username, password);
			h.set("User-Agent", integrator);
			h.set("Accept", "application/json");
		}).requestInterceptor((request, body, execution) -> {
			request.getHeaders().set("x-correlationid", UUID.randomUUID().toString());
			return execution.execute(request, body);
		}).build();
	}

	@Override
	public ConnectionCheck check() {
		try {
			Map<?, ?> page = listing.get().uri(LISTINGS, merchantId, 0, 1).retrieve().body(Map.class);
			Object total = page == null ? null : page.get("totalCount");
			return new ConnectionCheck(true, "Hepsiburada'ya bağlanıldı" + (total == null ? "" : " · " + total
				+ " ilan") + ".");
		} catch (RestClientException e) {
			return new ConnectionCheck(false, ProviderErrors.describe(PLATFORM, e));
		}
	}

	/** Butun ilanlar; stogu Hepsiburada'nin tuttugu (HB depodan gonderilen) ilanlar alinmaz. */
	@Override
	public List<ExternalProduct> products() {
		try {
			List<ExternalProduct> result = new ArrayList<>();
			for (int page = 0; page < MAX_PAGES; page++) {
				Map<?, ?> body = listing.get().uri(LISTINGS, merchantId, page * LISTING_PAGE, LISTING_PAGE)
					.retrieve().body(Map.class);
				List<Map<?, ?>> listings = HepsiburadaMapping.list(body, "listings");
				listings.stream().map(HepsiburadaMapping::product).filter(p -> p != null).forEach(result::add);
				if (listings.size() < LISTING_PAGE || (page + 1) * LISTING_PAGE >= HepsiburadaMapping.total(body)) {
					break;
				}
			}
			return result;
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/**
	 * Odemesi tamamlanmis, henuz paketlenmemis siparis kalemleri; Hepsiburada kalem kalem doner, siparis numarasina
	 * gore toplanir. Liste yalniz acik kalemleri tuttugu icin {@code changedAfter} kullanilmaz: her seferinde hepsi
	 * gelir, daha once alinan siparis atlanir.
	 */
	@Override
	public List<ExternalOrder> orders(LocalDateTime changedAfter) {
		try {
			List<Map<?, ?>> lines = new ArrayList<>();
			for (int page = 0; page < MAX_PAGES; page++) {
				Map<?, ?> body = oms.get().uri(ORDERS, merchantId, page * ORDER_PAGE, ORDER_PAGE).retrieve()
					.body(Map.class);
				List<Map<?, ?>> items = HepsiburadaMapping.list(body, "items");
				lines.addAll(items);
				if (items.size() < ORDER_PAGE || (page + 1) * ORDER_PAGE >= HepsiburadaMapping.total(body)) {
					break;
				}
			}
			return HepsiburadaMapping.orders(lines);
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/**
	 * Stoklar Hepsiburada SKU'suyla toplu gonderilir; Hepsiburada isi kuyruga alip numarasini doner. Sonuc o numarayla
	 * birkac kez sorgulanir; hala islenmiyorsa kalemler gonderildi sayilir.
	 */
	@Override
	public List<StockUpdate.Result> pushStock(List<StockUpdate> updates) {
		List<StockUpdate.Result> results = new ArrayList<>();
		try {
			for (int from = 0; from < updates.size(); from += STOCK_BATCH) {
				List<StockUpdate> chunk = updates.subList(from, Math.min(updates.size(), from + STOCK_BATCH));
				List<Map<String, Object>> items = chunk.stream()
					.map(u -> Map.<String, Object>of("hepsiburadaSku", u.productId(), "availableStock",
						Math.max(0, u.quantity()))).toList();
				Map<?, ?> queued = listing.post().uri(STOCK, merchantId).body(items).retrieve().body(Map.class);
				String id = queued == null || queued.get("id") == null ? null : String.valueOf(queued.get("id"));
				results.addAll(HepsiburadaMapping.stockResults(chunk, awaitResult(id)));
			}
			return results;
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/**
	 * Hazirlaniyor: siparisin kalemleri tek pakete alinir (Hepsiburada'da "paketlendi"); bundan sonra kargo etiketi
	 * ve fatura adimlari gelir.
	 */
	@Override
	public void reportPreparing(ExternalOrder order) {
		List<Map<String, Object>> lines = new ArrayList<>();
		for (ExternalOrder.Line line : order.lines()) {
			if (line.lineId() == null) {
				throw new ProviderException("Hepsiburada kalem numarası yok; sipariş eski bir çekimden.", null);
			}
			lines.add(Map.of("id", line.lineId(), "quantity", line.quantity()));
		}
		try {
			oms.post().uri(PACKAGES, merchantId).body(Map.of("lineItemRequests", lines, "parcelQuantity", 1))
				.retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	private Map<?, ?> awaitResult(String id) {
		if (id == null) {
			return null;
		}
		Map<?, ?> result = null;
		for (int attempt = 0; attempt < RESULT_ATTEMPTS; attempt++) {
			result = listing.get().uri(STOCK_RESULT, merchantId, id).retrieve().body(Map.class);
			if (!HepsiburadaMapping.inProgress(result)) {
				return result;
			}
			try {
				Thread.sleep(RESULT_WAIT.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return result;
			}
		}
		return result;
	}

}
