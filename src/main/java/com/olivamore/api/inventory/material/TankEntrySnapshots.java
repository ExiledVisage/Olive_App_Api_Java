package com.olivamore.api.inventory.material;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Soy agacinin tank dugumu icin tanka giris aninin snapshot'i. Hesap tank defterinde (storagemovement paketi)
 * yapilir; o paket materyal paketine bagimli oldugu icin arayuz burada, uygulamasi orada (bagimlilik tek yonlu).
 */
public interface TankEntrySnapshots {

	/**
	 * Tank, zaman, giren miktar, giristen once ve sonra tank materyalinin bu tanktaki miktari ve giristen sonraki
	 * karisim degeri.
	 */
	record EntrySnapshot(Long movementId, Long sourceMaterialId, Long tankMaterialId, Long storageUnitId,
		String storageUnitName, String storageUnitCode, LocalDateTime movedAt, BigDecimal quantity, String unit,
		BigDecimal balanceBefore, BigDecimal balanceAfter, List<MixValue> mixAfter) {
	}

	record MixValue(Long definitionId, String attributeKey, String label, String unit, BigDecimal value) {
	}

	/** Giris hareketlerinin snapshot'lari: hareket id -> snapshot (giris olmayan hareketler atlanir). */
	Map<Long, EntrySnapshot> snapshots(Collection<Long> movementIds);

}
