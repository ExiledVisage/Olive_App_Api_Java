package com.olivamore.api.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Materyal detayinda gosterilen dolum kosulari; bitmis urun modulu uygular. */
public interface MaterialRunDirectory {

	record MaterialRun(Long id, String status, String productName, Integer quantity, BigDecimal quantityUsed,
		LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime createdAt) {
	}

	/** Bu materyalden cekilen kosular, en yeni once; kullanilan miktar bu materyalinki. */
	List<MaterialRun> runsOf(long materialId);

}
