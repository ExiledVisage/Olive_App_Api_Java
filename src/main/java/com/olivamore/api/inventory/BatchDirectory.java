package com.olivamore.api.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Materyal cevaplarinin ve tank kurallarinin istedigi parti bilgileri; uretim modulu uygular. */
public interface BatchDirectory {

	record Batch(Long id, String batchCode, String status) {
	}

	record BatchProcess(Long id, Long productionBatchId, Long productionStageId, String status) {
	}

	record BatchOutput(Long productionProductTypeId, String name) {
	}

	/** Bulunamayan id'ler haritada yok. */
	Map<Long, Batch> batches(Collection<Long> ids);

	/** Bulunamayan id'ler haritada yok. */
	Map<Long, BatchProcess> batchProcesses(Collection<Long> ids);

	default Optional<BatchProcess> batchProcess(long id) {
		return Optional.ofNullable(batchProcesses(List.of(id)).get(id));
	}

	boolean batchProcessExists(long id);

	Optional<String> batchCode(long batchId);

	/** Partinin cikti urun tipleri ve adlari, sirayla. */
	List<BatchOutput> batchOutputs(long batchId);

	/** Kodunda aranan metin gecen partiler (buyuk-kucuk harf duyarsiz). */
	List<Long> batchIdsWithCode(String like);

}
