package com.olivamore.api.analytics.analytics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Karsilastirilabilen bir grup (docs/COMPARE.md): tedarikci, tarla, zeytin cesidi, teslimat, alim kanali, hat,
 * musteri, bitmis urun, tank, makine, donem. Motor ({@link Comparison}) secimi dogrular, en iyi / en kotuyu isaretler
 * ve aylik seriyi kurar; grup kalemlerini bulur, verisini bir kez yukler ve bir kalemin degerlerini hesaplar.
 */
interface CompareSubject {

	/** Karsilastirilan kalem; {@code detail} secicide adin altinda. */
	record Item(String id, String name, String detail) {
	}

	/**
	 * @param higherIsBetter null: yonu yok (en iyi / en kotu isaretlenmez)
	 * @param price yalnizca fiyat yetkisiyle gorunur
	 * @param series aylik grafikte cizilebilir (sayisal ve doneme bagli)
	 */
	record Metric(String key, String label, String unit, String group, Boolean higherIsBetter, boolean price,
		boolean series) {
	}

	/** Istek: aralik (donem karsilastirmasinda null), urun tipi, tedarikci ve tarla kapsami. */
	record Context(Range range, Long productTypeId, Long supplierId, Long fieldId) {
	}

	/** Yuklenmis veri; bir kalemin degerleri zaman suzgecine uyan kayitlardan (aylik seri icin). */
	interface Values {

		Map<String, Object> of(Item item, Predicate<LocalDateTime> when);

	}

	String key();

	String label();

	List<Metric> metrics();

	/** Secicideki kalemler: hareketli gruplarda araliktakiler, en buyukten. */
	List<Item> options(Context context);

	/** Kimliklerin kalemleri; gecersiz ya da bulunmayan kimlik dusurulur. */
	List<Item> items(List<String> ids);

	Values load(Context context, List<Item> items);

	/** Grup yalnizca fiyat yetkisiyle (alim kanali). */
	default boolean pricesOnly() {
		return false;
	}

	/** false: kalemler kendi donemini tasir (donem karsilastirmasi); aralik ve aylik seri yok. */
	default boolean usesRange() {
		return true;
	}

	/** Urun tipi suzgeci uygulanir (uretim gruplari). */
	default boolean filtersProductType() {
		return false;
	}

	static List<Long> longs(List<String> ids) {
		return ids.stream().map(id -> {
			try {
				return Long.parseLong(id);
			}
			catch (NumberFormatException e) {
				return null;
			}
		}).filter(java.util.Objects::nonNull).toList();
	}

}
