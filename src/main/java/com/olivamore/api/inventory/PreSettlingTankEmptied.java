package com.olivamore.api.inventory;

import java.math.BigDecimal;
import java.util.List;

/**
 * On dinlendirme tanki bosaltildi (C4): ayni islemde yayinlanir, uretim modulu dinlenmedeki asamalari tamamlar ve
 * tortu materyallerini acar. Dinleyicinin hatasi bosaltmanin tamamini geri alir.
 * @param entries ön dinlendirme asamasiyla girmis parti materyalleri; asamasiz (eski) materyaller yok
 */
public record PreSettlingTankEmptied(long storageUnitId, List<Entry> entries) {

	/**
	 * Kapanan parti materyali ve onu tanka sokan asama.
	 * @param residueKg tanka giren ile cekilen arasindaki fark (kutle, kg); sifirsa tortu yok
	 */
	public record Entry(long materialId, long productionBatchId, long batchProcessId, BigDecimal residueKg) {
	}

}
