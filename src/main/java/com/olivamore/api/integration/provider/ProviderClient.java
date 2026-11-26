package com.olivamore.api.integration.provider;

/**
 * Bir platform baglantisinin istemcisi. Her platform kendi paketinde uygular; ortak kod (eslestirme, siparis alma,
 * stok kuyrugu) yalniz bu arayuzu bilir.
 */
public interface ProviderClient {

	/** Erisim bilgileri ve adres calisiyor mu; calismiyorsa kullaniciya gosterilecek gerekce. */
	ConnectionCheck check();

	/** @param message basariliysa magaza/hesap ozeti, degilse hatanin gerekcesi */
	record ConnectionCheck(boolean ok, String message) {
	}

}
