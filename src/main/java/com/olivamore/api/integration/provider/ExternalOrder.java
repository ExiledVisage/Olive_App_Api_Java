package com.olivamore.api.integration.provider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Platformdaki siparis, platformdan bagimsiz bicimde.
 *
 * @param externalId platformdaki siparis id'si (tekrar almayi onler)
 * @param number     musterinin gordugu siparis numarasi ("1234")
 * @param createdAt  UTC
 */
public record ExternalOrder(String externalId, String number, String status, LocalDateTime createdAt,
	Customer customer, Address address, List<Line> lines, String note) {

	public record Customer(String name, String company, String email, String phone) {
	}

	/** @param line adres satirlari birlesik (sokak, bina, daire) */
	public record Address(String country, String city, String district, String line, String postalCode) {
	}

	/**
	 * @param variantId varyantsiz urunde bos metin
	 * @param lineId    platformdaki kalem id'si (durum bildiriminde kalemler bununla gider); eski kayitlarda bos
	 * @param unitPrice KDV dahil, indirim sonrasi birim fiyat; platform vermediyse bos
	 */
	public record Line(String productId, String variantId, String name, String sku, int quantity, String lineId,
		BigDecimal unitPrice) {
	}

}
