package com.olivamore.api.packaging;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/** Lot detayinda gosterilen dolum kosulari; bitmis urun modulu uygular. */
public interface PackagingRunDirectory {

	record Run(Long id, String status, Integer quantity, LocalDateTime completedAt, String productName) {
	}

	/** Bulunamayan id'ler haritada yok. */
	Map<Long, Run> runs(Collection<Long> ids);

}
