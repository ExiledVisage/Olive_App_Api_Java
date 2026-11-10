package com.olivamore.api.finishedproduct;

import java.util.Collection;
import java.util.Set;

/** Stok hesabinin ve silme kontrolunun istedigi siparis bilgileri; satis modulu uygular. */
public interface OrderDirectory {

	/** Verilen siparislerden durumu hazir olanlar. */
	Set<Long> readyAmong(Collection<Long> orderIds);

	/** Urunu iceren siparis kalemi sayisi. */
	long itemCount(long productId);

}
