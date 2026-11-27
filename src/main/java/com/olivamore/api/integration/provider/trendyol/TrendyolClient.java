package com.olivamore.api.integration.provider.trendyol;

import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.ExternalProduct;
import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.integration.provider.ProviderErrors;
import com.olivamore.api.integration.provider.ProviderException;
import com.olivamore.api.integration.provider.ShippingLabel;
import com.olivamore.api.integration.provider.StockUpdate;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trendyol satici API istemcisi ({@code https://apigw.trendyol.com/integration}); satici ID, API anahtari ve gizli
 * anahtarla Basic auth, her istekte {@code storeFrontCode: TR}. Trendyol urunu ve stogu barkodla tanir: eslestirme
 * anahtari barkod, platform urun id'si de barkoddur. Cevaplarin okunmasi {@link TrendyolMapping}'de.
 */
public class TrendyolClient implements ProviderClient {

	static final String PLATFORM = "Trendyol";
	public static final String LIVE_URL = "https://apigw.trendyol.com/integration";

	/** Siparis paketi sorgusunda en fazla 200 kayit, tarih araligi en fazla iki hafta. */
	private static final int ORDER_PAGE = 200;
	private static final Duration ORDER_WINDOW = Duration.ofDays(14);
	private static final String ORDERS = "/order/sellers/{seller}/v2/orders?status=Created&startDate={start}"
		+ "&endDate={end}&orderByField=PackageLastModifiedDate&orderByDirection=ASC&page={page}&size={size}";
	private static final String PACKAGE = "/order/sellers/{seller}/shipment-packages/{package}";

	private static final String LABEL = "/sellers/{seller}/common-label/{tracking}";
	private static final Duration LABEL_WAIT = Duration.ofSeconds(1);

	/** Tedarik edememe gerekcesi: 500 stok tukendi. */
	private static final int OUT_OF_STOCK = 500;

	/** Fatura tarihi Turkiye gunu. */
	private static final ZoneId LOCAL = ZoneId.of("Europe/Istanbul");

	/** Urun listesinde sayfa basina kayit. */
	private static final int PRODUCT_PAGE = 200;

	/** Stok-fiyat gonderiminde istek basina en fazla 1000 kalem. */
	private static final int STOCK_BATCH = 1000;

	/** Trendyol'un urun basina kabul ettigi en fazla stok. */
	private static final int MAX_STOCK = 20000;
	private static final int MAX_PAGES = 100;

	/** Kuyruga alinan stok isinin sonucu icin bekleme. */
	private static final int RESULT_ATTEMPTS = 5;
	private static final Duration RESULT_WAIT = Duration.ofSeconds(2);

	private final RestClient http;
	private final String sellerId;

	public TrendyolClient(String baseUrl, String sellerId, String apiKey, String apiSecret) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.sellerId = sellerId;
		this.http = RestClient.builder().requestFactory(factory).baseUrl(baseUrl == null ? LIVE_URL : baseUrl)
			.defaultHeaders(h -> {
				h.setBasicAuth(apiKey, apiSecret);
				h.set("storeFrontCode", "TR");
				h.set("User-Agent", sellerId + " - SelfIntegration");
			}).build();
	}

	@Override
	public ConnectionCheck check() {
		try {
			Map<?, ?> page = http.get()
				.uri("/product/sellers/{seller}/products?approved=true&page=0&size=1", sellerId).retrieve()
				.body(Map.class);
			Object total = page == null ? null : page.get("totalElements");
			return new ConnectionCheck(true, "Trendyol'a bağlanıldı" + (total == null ? "" : " · " + total
				+ " onaylı ürün") + ".");
		} catch (RestClientException e) {
			return new ConnectionCheck(false, ProviderErrors.describe(PLATFORM, e));
		}
	}

	@Override
	public List<ExternalProduct> products() {
		try {
			List<ExternalProduct> result = new ArrayList<>();
			for (int page = 0; page < MAX_PAGES; page++) {
				Map<?, ?> body = http.get()
					.uri("/product/sellers/{seller}/products?approved=true&page={page}&size={size}", sellerId, page,
						PRODUCT_PAGE).retrieve().body(Map.class);
				List<Map<?, ?>> content = TrendyolMapping.content(body);
				content.stream().map(TrendyolMapping::product).filter(p -> p != null).forEach(result::add);
				if (content.isEmpty() || page + 1 >= TrendyolMapping.totalPages(body)) {
					break;
				}
			}
			return result;
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/** Hazirlanmayi bekleyen (Created) paketler; aralik en fazla iki hafta, son degisime gore eskiden yeniye. */
	@Override
	public List<ExternalOrder> orders(LocalDateTime changedAfter) {
		LocalDateTime now = Timestamps.now();
		LocalDateTime earliest = now.minus(ORDER_WINDOW).plusMinutes(1);
		LocalDateTime from = changedAfter == null || changedAfter.isBefore(earliest) ? earliest : changedAfter;
		long start = from.toInstant(ZoneOffset.UTC).toEpochMilli();
		// Bitis bir dakika ileride: saniyeye kirpilmis "simdi" az once degisen paketi disarida birakmasin.
		long end = now.plusMinutes(1).toInstant(ZoneOffset.UTC).toEpochMilli();
		try {
			List<ExternalOrder> result = new ArrayList<>();
			for (int page = 0; page < MAX_PAGES; page++) {
				Map<?, ?> body = http.get().uri(ORDERS, sellerId, start, end, page, ORDER_PAGE).retrieve()
					.body(Map.class);
				List<Map<?, ?>> content = TrendyolMapping.content(body);
				content.stream().map(TrendyolMapping::order).forEach(result::add);
				if (content.isEmpty() || page + 1 >= TrendyolMapping.totalPages(body)) {
					break;
				}
			}
			return result;
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/**
	 * Stoklar barkodla toplu gonderilir; Trendyol isi kuyruga alip numarasini doner. Sonuc o numarayla birkac kez
	 * sorgulanir; hala islenmiyorsa kalemler gonderildi sayilir (Trendyol sirayla isler).
	 */
	@Override
	public List<StockUpdate.Result> pushStock(List<StockUpdate> updates) {
		List<StockUpdate.Result> results = new ArrayList<>();
		try {
			for (int from = 0; from < updates.size(); from += STOCK_BATCH) {
				List<StockUpdate> chunk = updates.subList(from, Math.min(updates.size(), from + STOCK_BATCH));
				List<Map<String, Object>> items = chunk.stream()
					.map(u -> Map.<String, Object>of("barcode", u.productId(), "quantity",
						Math.min(u.quantity(), MAX_STOCK))).toList();
				Map<?, ?> queued = http.post().uri("/inventory/sellers/{seller}/products/price-and-inventory", sellerId)
					.body(Map.of("items", items)).retrieve().body(Map.class);
				String batchId = queued == null ? null : String.valueOf(queued.get("batchRequestId"));
				results.addAll(TrendyolMapping.stockResults(chunk, awaitResult(batchId)));
			}
			return results;
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/**
	 * Paket durumu "Picking" (hazirlaniyor): pakette butun kalemler adetleriyle gider. Bundan sonra musteri paketi
	 * Trendyol'dan iptal edemez.
	 */
	@Override
	public String reportPreparing(ExternalOrder order) {
		List<Map<String, Object>> lines = lines(order);
		try {
			http.put().uri(PACKAGE, sellerId, order.externalId())
				.body(Map.of("lines", lines, "params", Map.of(), "status", "Picking")).retrieve().toBodilessEntity();
			return null;
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/**
	 * Ortak etiket: paketin kargo takip numarasi paket sorgusundan okunur, etiket {@code POST
	 * /sellers/{id}/common-label/{takip}} ile istenir ve {@code GET} ile alinir (yalniz ZPL). Trendyol'un anlasmali
	 * kargosu (TEX, Aras) icin; hazir olmayan etiket birkac kez beklenir.
	 */
	@Override
	public ShippingLabel label(ExternalOrder order, String platformRef) {
		try {
			Map<?, ?> page = http.get().uri("/order/sellers/{seller}/orders?shipmentPackageIds={package}", sellerId,
				order.externalId()).retrieve().body(Map.class);
			Map<?, ?> pkg = TrendyolMapping.content(page).stream().findFirst()
				.orElseThrow(() -> new ProviderException("Trendyol paketi bulunamadı.", null));
			String tracking = TrendyolMapping.text(pkg.get("cargoTrackingNumber"));
			if (tracking == null) {
				throw new ProviderException("Trendyol paketin kargo takip numarasını henüz vermedi.", null);
			}
			http.post().uri(LABEL, sellerId, tracking).body(Map.of("format", "ZPL", "boxQuantity", 1)).retrieve()
				.toBodilessEntity();
			List<String> labels = List.of();
			for (int attempt = 0; attempt < RESULT_ATTEMPTS && labels.isEmpty(); attempt++) {
				if (attempt > 0) {
					Thread.sleep(LABEL_WAIT.toMillis());
				}
				labels = TrendyolMapping.labels(http.get().uri(LABEL, sellerId, tracking).retrieve().body(Map.class));
			}
			if (labels.isEmpty()) {
				throw new ProviderException("Trendyol etiketi henüz hazırlamadı; biraz sonra tekrar deneyin.", null);
			}
			return new ShippingLabel(TrendyolMapping.text(pkg.get("cargoProviderName")), tracking, "ZPL", labels);
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProviderException("Trendyol etiketi beklenirken durduruldu.", e);
		}
	}

	/** Paketin butun kalemleri, gerekce 500 "Stok tukendi"; Trendyol paketi iptal eder. */
	@Override
	public void reportUnsupplied(ExternalOrder order) {
		try {
			http.put().uri(PACKAGE + "/items/unsupplied", sellerId, order.externalId())
				.body(Map.of("lines", lines(order), "reasonId", OUT_OF_STOCK)).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/** Paket durumu "Invoiced": butun kalemler ve {@code params.invoiceNumber}. */
	@Override
	public void reportInvoiced(ExternalOrder order, String invoiceNumber) {
		try {
			http.put().uri(PACKAGE, sellerId, order.externalId())
				.body(Map.of("lines", lines(order), "params", Map.of("invoiceNumber", invoiceNumber), "status",
					"Invoiced")).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/**
	 * {@code POST /sellers/{id}/seller-invoice-file} (multipart: paket, dosya, fatura tarihi saniye, numara). Pakette
	 * zaten fatura varsa Trendyol 409 doner; gonderilmis sayilir.
	 */
	@Override
	public void sendInvoiceFile(ExternalOrder order, String invoiceNumber, LocalDate issueDate, byte[] pdf) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("shipmentPackageId", order.externalId());
		parts.add("invoiceNumber", invoiceNumber);
		parts.add("invoiceDateTime", String.valueOf(issueDate.atStartOfDay(LOCAL).toEpochSecond()));
		parts.add("file", new ByteArrayResource(pdf) {
			@Override
			public String getFilename() {
				return invoiceNumber + ".pdf";
			}
		});
		try {
			http.post().uri("/sellers/{seller}/seller-invoice-file", sellerId)
				.contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().toBodilessEntity();
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().value() != 409) {
				throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
			}
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	private static List<Map<String, Object>> lines(ExternalOrder order) {
		List<Map<String, Object>> lines = new ArrayList<>();
		for (ExternalOrder.Line line : order.lines()) {
			if (line.lineId() == null) {
				throw new ProviderException("Trendyol kalem numarası yok; sipariş eski bir çekimden.", null);
			}
			lines.add(Map.of("lineId", Long.parseLong(line.lineId()), "quantity", line.quantity()));
		}
		return lines;
	}

	private Map<?, ?> awaitResult(String batchId) {
		if (batchId == null || "null".equals(batchId)) {
			return null;
		}
		Map<?, ?> result = null;
		for (int attempt = 0; attempt < RESULT_ATTEMPTS; attempt++) {
			result = http.get().uri("/product/sellers/{seller}/products/batch-requests/{batch}", sellerId, batchId)
				.retrieve().body(Map.class);
			if (!TrendyolMapping.inProgress(result)) {
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
