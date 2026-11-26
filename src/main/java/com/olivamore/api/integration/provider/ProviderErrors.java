package com.olivamore.api.integration.provider;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform cevabindaki HTTP hatalarini kullanicinin anlayacagi gerekceye cevirir; butun istemciler kullanir. */
public final class ProviderErrors {

	private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]{1,300})\"");

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
				default -> platform + " hata döndü (" + status + ")" + reason(response) + ".";
			};
		}
		if (error instanceof ResourceAccessException) {
			return platform + "'a ulaşılamadı: adres yanlış ya da site kapalı.";
		}
		return platform + " ile konuşulamadı: " + error.getMessage();
	}

	/** Cevaptaki ilk {@code "message"} (Trendyol ve WooCommerce hatayi boyle yazar): ": mesaj", yoksa bos. */
	private static String reason(RestClientResponseException response) {
		Matcher m = MESSAGE.matcher(response.getResponseBodyAsString());
		return m.find() ? ": " + m.group(1) : "";
	}

}
