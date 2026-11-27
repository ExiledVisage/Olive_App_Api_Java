package com.olivamore.api.integration.provider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Baglanilabilen platformlar ve baglanti formunda istenen erisim bilgileri. {@code supported} false olan platform
 * listede "yakinda" gorunur, baglantisi kurulamaz.
 */
public enum Provider {

	WOOCOMMERCE("woocommerce", "WooCommerce (site)", true, true, List.of(
		new Field("consumer_key", "Tüketici anahtarı (ck_...)", true),
		new Field("consumer_secret", "Tüketici gizli anahtarı (cs_...)", true))),

	TRENDYOL("trendyol", "Trendyol", true, false, List.of(
		new Field("seller_id", "Satıcı ID", false),
		new Field("api_key", "API anahtarı", true),
		new Field("api_secret", "API gizli anahtarı", true))),

	HEPSIBURADA("hepsiburada", "Hepsiburada", true, false, List.of(
		new Field("merchant_id", "Mağaza (merchant) ID", false),
		new Field("username", "API kullanıcı adı", false),
		new Field("password", "API şifresi", true),
		new Field("integrator", "Entegratör adı", false))),

	/** Muhasebe: satis faturalari gider; siparis ve stok yok (docs/INVOICES.md). */
	PARASUT("parasut", "Paraşüt (muhasebe)", true, false, List.of(
		new Field("company_id", "Firma ID", false),
		new Field("client_id", "Uygulama (client) ID", false),
		new Field("client_secret", "Uygulama gizli anahtarı", true),
		new Field("username", "Paraşüt kullanıcı e-postası", false),
		new Field("password", "Paraşüt şifresi", true)));

	/** @param secret cevapta maskelenir */
	public record Field(String key, String label, boolean secret) {
	}

	private final String key;
	private final String label;
	private final boolean supported;
	private final boolean needsBaseUrl;
	private final List<Field> fields;

	Provider(String key, String label, boolean supported, boolean needsBaseUrl, List<Field> fields) {
		this.key = key;
		this.label = label;
		this.supported = supported;
		this.needsBaseUrl = needsBaseUrl;
		this.fields = fields;
	}

	public String key() {
		return key;
	}

	public String label() {
		return label;
	}

	public boolean supported() {
		return supported;
	}

	/**
	 * Site adresi zorunlu mu (WooCommerce). Pazar yerinde adres sabittir; verilirse (test ortami) o kullanilir.
	 */
	public boolean needsBaseUrl() {
		return needsBaseUrl;
	}

	public List<Field> fields() {
		return fields;
	}

	/**
	 * Siparis hazirlanmaya baslayinca platforma bildirilir mi (Trendyol paketi "hazirlaniyor", Hepsiburada'da
	 * kalemler paketlenir). WooCommerce'te odenmis siparis zaten "isleniyor" durumundadir.
	 */
	public boolean reportsPreparing() {
		return this == TRENDYOL || this == HEPSIBURADA;
	}

	/** Muhasebe programi mi (satis kanali degil): urun, siparis ve stok isleri yok, faturalar gider. */
	public boolean accounting() {
		return this == PARASUT;
	}

	public static Optional<Provider> of(String key) {
		return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
	}

}
