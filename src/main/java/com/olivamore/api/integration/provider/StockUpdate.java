package com.olivamore.api.integration.provider;

/**
 * Platforma gonderilecek stok: urun (ya da varyant) ve satilabilir adet.
 *
 * @param variantId varyantsiz urunde bos metin
 */
public record StockUpdate(String productId, String variantId, int quantity) {

	/** Gonderimin sonucu; {@code error} basariliysa null. */
	public record Result(String productId, String variantId, String error) {

		public boolean ok() {
			return error == null;
		}

	}

}
