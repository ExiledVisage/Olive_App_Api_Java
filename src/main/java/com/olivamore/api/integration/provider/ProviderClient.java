package com.olivamore.api.integration.provider;

import java.time.LocalDateTime;
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

	/**
	 * Hazirlanmayi bekleyen (odenmis) siparisler; {@code changedAfter} verilirse yalniz o andan sonra degisenler.
	 *
	 * @param changedAfter UTC
	 */
	List<ExternalOrder> orders(LocalDateTime changedAfter);

	/** @param message basariliysa magaza/hesap ozeti, degilse hatanin gerekcesi */
	record ConnectionCheck(boolean ok, String message) {
	}

}
