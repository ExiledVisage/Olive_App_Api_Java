package com.olivamore.api.supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tedarikcinin teslimatlari uretim partilerinden gelir; uretim modulu tedarikcilere bagimli oldugu icin bunu o
 * uygular.
 */
public interface SupplierDeliverySource {

	record BatchDelivery(Long id, String batchCode, String status, LocalDateTime receivedAt, Long supplierFieldId,
		BigDecimal quantityKg) {
	}

	record WeighIn(Long id, Long batchId, String quantityJson, LocalDateTime completedAt) {
	}

	record WeighInValue(Long processId, String fieldKey, String label, String valueText, BigDecimal valueDecimal,
		Long valueInteger) {
	}

	/** En yeni varis once. */
	List<BatchDelivery> batches(long supplierId);

	/** Partinin girdi urun tiplerinin adlari (bos adlar yok), sirayla. */
	Map<Long, List<String>> inputProductNames(List<Long> batchIds);

	/** Partinin kategorisi "weighing" olan prosesleri arasindan asama sirasi en kucuk olan. */
	Map<Long, WeighIn> firstWeighIns(List<Long> batchIds);

	/** Proseslerin alan degerleri, kayit sirasinda. */
	List<WeighInValue> fieldValues(List<Long> processIds);

}
