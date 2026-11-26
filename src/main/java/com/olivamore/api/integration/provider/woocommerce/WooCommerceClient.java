package com.olivamore.api.integration.provider.woocommerce;

import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.integration.provider.ProviderErrors;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WooCommerce REST API v3 istemcisi ({@code /wp-json/wc/v3}); tuketici anahtari ve gizli anahtariyla Basic auth
 * (HTTPS). Siteye ozel her sey bu pakette; ortak kod {@link ProviderClient} uzerinden konusur.
 */
public class WooCommerceClient implements ProviderClient {

	static final String PLATFORM = "WooCommerce";

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
			return new ConnectionCheck(true, "Siteye bağlanıldı" + (currency == null ? "" : " · para birimi " + currency)
				+ (products == null || products.isEmpty() ? " · sitede ürün yok" : "") + ".");
		} catch (RestClientException e) {
			return new ConnectionCheck(false, ProviderErrors.describe(PLATFORM, e));
		}
	}

}
