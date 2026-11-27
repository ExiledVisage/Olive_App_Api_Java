package com.olivamore.api.integration.provider;

import com.olivamore.api.integration.provider.hepsiburada.HepsiburadaClient;
import com.olivamore.api.integration.provider.parasut.ParasutClient;
import com.olivamore.api.integration.provider.trendyol.TrendyolClient;
import com.olivamore.api.integration.provider.woocommerce.WooCommerceClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Baglantinin platformuna gore istemciyi kurar; yeni platform buraya bir satirla eklenir. */
@Component
public class ProviderClients {

	public ProviderClient create(Provider provider, String baseUrl, Map<String, String> credentials) {
		return switch (provider) {
			case WOOCOMMERCE -> new WooCommerceClient(baseUrl, credentials.get("consumer_key"),
				credentials.get("consumer_secret"));
			case TRENDYOL -> new TrendyolClient(baseUrl, credentials.get("seller_id"), credentials.get("api_key"),
				credentials.get("api_secret"));
			case HEPSIBURADA -> new HepsiburadaClient(baseUrl, credentials.get("merchant_id"),
				credentials.get("username"), credentials.get("password"), credentials.get("integrator"));
			case PARASUT -> new ParasutClient(baseUrl, credentials.get("company_id"), credentials.get("client_id"),
				credentials.get("client_secret"), credentials.get("username"), credentials.get("password"));
		};
	}

}
