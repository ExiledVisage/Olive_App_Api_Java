package com.olivamore.api.integration.provider;

import com.olivamore.api.integration.provider.woocommerce.WooCommerceClient;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Baglantinin platformuna gore istemciyi kurar; yeni platform buraya bir satirla eklenir. */
@Component
public class ProviderClients {

	public ProviderClient create(Provider provider, String baseUrl, Map<String, String> credentials) {
		return switch (provider) {
			case WOOCOMMERCE -> new WooCommerceClient(baseUrl, credentials.get("consumer_key"),
				credentials.get("consumer_secret"));
			case TRENDYOL, HEPSIBURADA -> throw new BusinessRuleException(provider.label()
				+ " bağlantısı henüz desteklenmiyor.");
		};
	}

}
