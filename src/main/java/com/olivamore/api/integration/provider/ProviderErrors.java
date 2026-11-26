package com.olivamore.api.integration.provider;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Platform cevabindaki HTTP hatalarini kullanicinin anlayacagi gerekceye cevirir; butun istemciler kullanir. */
public final class ProviderErrors {

	private ProviderErrors() {
	}

	public static String describe(String platform, Exception error) {
		if (error instanceof RestClientResponseException response) {
			int status = response.getStatusCode().value();
			return switch (status) {
				case 401, 403 -> platform + " erişim bilgilerini reddetti (" + status
					+ "). Anahtarı ve okuma/yazma iznini kontrol edin.";
				case 404 -> platform + " bu adreste bulunamadı (404). Adresi kontrol edin.";
				case 429 -> platform + " çok fazla istek dedi (429); biraz sonra tekrar denenecek.";
				default -> platform + " hata döndü (" + status + ").";
			};
		}
		if (error instanceof ResourceAccessException) {
			return platform + "'a ulaşılamadı: adres yanlış ya da site kapalı.";
		}
		return platform + " ile konuşulamadı: " + error.getMessage();
	}

}
