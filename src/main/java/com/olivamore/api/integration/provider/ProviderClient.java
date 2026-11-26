package com.olivamore.api.integration.provider;

import java.util.List;

/**
 * Bir platform baglantisinin istemcisi. Her platform kendi paketinde uygular; ortak kod (eslestirme, siparis alma,
 * stok kuyrugu) yalniz bu arayuzu bilir.
 */
public interface ProviderClient {

	/** Erisim bilgileri ve adres calisiyor mu; calismiyorsa kullaniciya gosterilecek gerekce. */
	ConnectionCheck check();

	/** Platformdaki butun satilabilir urunler (varyantli urunde her varyant ayri). */
	List<ExternalProduct> products();

	/** @param message basariliysa magaza/hesap ozeti, degilse hatanin gerekcesi */
	record ConnectionCheck(boolean ok, String message) {
	}

}
