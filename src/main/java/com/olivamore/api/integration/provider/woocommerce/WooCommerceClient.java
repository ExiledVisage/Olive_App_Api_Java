package com.olivamore.api.integration.provider.woocommerce;

import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.ExternalProduct;
import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.integration.provider.ProviderErrors;
import com.olivamore.api.integration.provider.ProviderException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WooCommerce REST API v3 istemcisi ({@code /wp-json/wc/v3}); tuketici anahtari ve gizli anahtariyla Basic auth
 * (HTTPS). Siteye ozel her sey bu pakette; ortak kod {@link ProviderClient} uzerinden konusur. Cevaplarin
 * okunmasi {@link WooCommerceProducts}'ta.
 */
public class WooCommerceClient implements ProviderClient {

	static final String PLATFORM = "WooCommerce";

	/** WooCommerce'in sayfa basina izin verdigi en fazla kayit. */
	private static final int PAGE_SIZE = 100;

	/** Sonsuz donguye karsi: 100 x 100 = 10.000 urun. */
	private static final int MAX_PAGES = 100;

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST =
		new ParameterizedTypeReference<>() {
		};

	/** Saniyeli ISO bicimi ("2026-11-27T09:00:00"); toString saniye sifirsa saniyeyi atar. */
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final RestClient http;

	public WooCommerceClient(String baseUrl, String consumerKey, String consumerSecret) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.http = RestClient.builder().requestFactory(factory).baseUrl(baseUrl + "/wp-json/wc/v3")
			.defaultHeaders(h -> h.setBasicAuth(consumerKey, consumerSecret)).build();
	}

	@Override
	public ConnectionCheck check() {
		try {
			List<?> products = http.get().uri("/products?per_page=1").retrieve().body(List.class);
			Map<?, ?> settings = http.get().uri("/settings/general/woocommerce_currency").retrieve().body(Map.class);
			String currency = settings == null ? null : String.valueOf(settings.get("value"));
			return new ConnectionCheck(true, "Siteye bağlanıldı"
				+ (currency == null ? "" : " · para birimi " + currency)
				+ (products == null || products.isEmpty() ? " · sitede ürün yok" : "") + ".");
		} catch (RestClientException e) {
			return new ConnectionCheck(false, ProviderErrors.describe(PLATFORM, e));
		}
	}

	@Override
	public List<ExternalProduct> products() {
		try {
			List<ExternalProduct> result = new ArrayList<>();
			for (Map<String, Object> product : pages("/products?status=publish&per_page=" + PAGE_SIZE)) {
				if (WooCommerceProducts.isVariable(product)) {
					String id = WooCommerceProducts.id(product);
					String variations = "/products/" + id + "/variations?per_page=" + PAGE_SIZE;
					for (Map<String, Object> variation : pages(variations)) {
						result.add(WooCommerceProducts.variation(product, variation));
					}
				} else {
					result.add(WooCommerceProducts.simple(product));
				}
			}
			return result;
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	@Override
	public List<ExternalOrder> orders(LocalDateTime changedAfter) {
		String path = "/orders?status=" + WooCommerceOrders.READY_STATUS + "&per_page=" + PAGE_SIZE
			+ "&orderby=date&order=asc&dates_are_gmt=true"
			+ (changedAfter == null ? "" : "&modified_after=" + ISO.format(changedAfter.withNano(0)));
		try {
			return pages(path).stream().map(WooCommerceOrders::order).toList();
		} catch (RestClientException e) {
			throw new ProviderException(ProviderErrors.describe(PLATFORM, e), e);
		}
	}

	/** Butun sayfalar; toplam sayfa {@code X-WP-TotalPages} basligindan. */
	private List<Map<String, Object>> pages(String path) {
		List<Map<String, Object>> all = new ArrayList<>();
		for (int page = 1; page <= MAX_PAGES; page++) {
			ResponseEntity<List<Map<String, Object>>> response = http.get().uri(path + "&page=" + page).retrieve()
				.toEntity(LIST);
			List<Map<String, Object>> body = response.getBody();
			if (body == null || body.isEmpty()) {
				break;
			}
			all.addAll(body);
			String total = response.getHeaders().getFirst("X-WP-TotalPages");
			if (total == null || page >= Integer.parseInt(total)) {
				break;
			}
		}
		return all;
	}

}
