package com.olivamore.api.finishedproduct;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Siseden zeytine izlenebilirlik sayfasinin istedigi teslimat bilgileri: tedarikci, tarla, varis ve cesit. Uretim
 * modulu uygular (uretim bu module bagimli degil; arayuz burada, uygulama orada).
 */
public interface RunOriginDirectory {

	/** @param cultivars teslimatin girdi urun tiplerinin adlari (Gemlik Zeytini gibi) */
	record Delivery(Long batchId, String batchCode, LocalDateTime receivedAt, String supplierName, String fieldCode,
		String fieldLocation, List<String> cultivars) {
	}

	/** Partilerin teslimatlari: grup ise ait oldugu teslimat; tekil, varis sirasiyla. */
	List<Delivery> deliveries(Collection<Long> batchIds);

}
