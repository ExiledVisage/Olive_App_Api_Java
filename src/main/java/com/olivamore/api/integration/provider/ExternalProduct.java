package com.olivamore.api.integration.provider;

/**
 * Platformdaki satilabilir urun ya da varyant, platformdan bagimsiz bicimde.
 *
 * @param productId platformdaki urun id'si
 * @param variantId varyant id'si; varyantsiz urunde bos metin
 * @param stock     platformdaki stok; platform stok tutmuyorsa null
 */
public record ExternalProduct(String productId, String variantId, String name, String sku, String barcode,
	Integer stock) {
}
