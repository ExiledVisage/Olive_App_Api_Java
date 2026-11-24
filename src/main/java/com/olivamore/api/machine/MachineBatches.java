package com.olivamore.api.machine;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Makine adimlarinin partileri; uretim modulu uygular (makine modulu uretime bagli degildir, uretim makineyi
 * kullanir). Randiman makineye ayrilamaz: parti butun makinelerden gecer, yag sonda olculur.
 */
public interface MachineBatches {

	/**
	 * Adimin asamasinin partisi: sikima giren zeytin (asama tamamlandiysa), on dinlendirmeden suzulen yag (kg) ve
	 * partinin randimani (randiman asamasi tamamlandiysa); bilinmeyen {@code null}.
	 */
	record Batch(long batchProcessId, long batchId, String batchCode, BigDecimal pressedKg, BigDecimal oilKg,
		BigDecimal yieldPercent) {
	}

	/** Asama id'sine gore; olmayan asama haritada yok. */
	Map<Long, Batch> batches(Collection<Long> batchProcessIds);

}
