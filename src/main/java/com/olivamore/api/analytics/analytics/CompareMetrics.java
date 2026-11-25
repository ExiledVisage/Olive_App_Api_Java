package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.CompareSubject.Metric;

import java.util.List;
import java.util.stream.Stream;

/** Gruplarin karsilastirilabilen ozellikleri (docs/COMPARE.md). */
final class CompareMetrics {

	/** Uretim: tedarikci, tarla, cesit, teslimat, kanal, hat ve donem. */
	static final List<Metric> PRODUCTION = List.of(
		number("delivery_count", "Getiris sayisi", "adet", "volume", true),
		number("incoming_kg", "Gelen zeytin", "kg", "volume", true),
		number("pressed_kg", "Sikilan zeytin", "kg", "volume", true),
		number("oil_kg", "Uretilen yag", "kg", "volume", true),
		number("yield_pct", "Randiman", "%", "yield", true),
		number("gross_yield_pct", "Brut randiman", "%", "yield", true),
		number("pomace_ratio", "Pirina orani", "%", "yield", false),
		number("residue_ratio", "Tortu / yag", "%", "yield", false),
		number("avg_acidity", "Ortalama asitlik", "%", "quality", false),
		number("olive_oil_ratio", "Zeytin yag orani (IAS)", "%", "quality", true),
		number("olive_kpy", "Zeytin KPY", "%", "quality", true),
		number("industrial_yield", "Beklenen randiman", "%", "quality", true),
		number("pomace_kpy", "Pirina KPY", "%", "quality", false),
		number("avg_peroxide", "Ortalama peroksit", "meq O2/kg", "quality", false),
		number("avg_k232", "Ortalama K232", null, "quality", false),
		number("extra_virgin_pct", "Sizma orani", "%", "quality", true),
		price("avg_price_per_kg", "Ort. kg fiyati (KDV haric)", "TL", false),
		price("avg_price_per_kg_with_vat", "Ort. kg fiyati (KDV dahil)", "TL", false),
		price("cost_per_kg", "Kg maliyeti (nakliye dahil)", "TL", false),
		price("purchase_total", "Alim tutari (KDV haric)", "TL", null),
		new Metric("main_channel", "Kanal", null, "pattern", null, true, false),
		text("last_delivery_at", "Son getiris"),
		number("avg_delivery_kg", "Ort. getiris", "kg", "pattern", null));

	static final List<Metric> CUSTOMER = List.of(
		number("order_count", "Siparis sayisi", "adet", "sales", true),
		number("sold_litres", "Alinan yag", "L", "sales", true),
		number("sold_quantity", "Alinan sise", "adet", "sales", true),
		number("avg_order_litres", "Ort. siparis", "L", "sales", true),
		number("product_variety", "Urun cesitliligi", "cesit", "pattern", null),
		number("avg_days_between_orders", "Siparis araligi", "gun", "pattern", false),
		text("top_product", "En cok aldigi urun"),
		text("last_order_at", "Son siparis"));

	static final List<Metric> FINISHED_PRODUCT = List.of(
		number("sold_litres", "Satilan yag", "L", "sales", true),
		number("sold_quantity", "Satilan adet", "adet", "sales", true),
		number("order_count", "Siparis sayisi", "adet", "sales", true),
		number("customer_count", "Musteri sayisi", "musteri", "sales", true),
		number("avg_quantity_per_order", "Siparis basina adet", "adet", "pattern", null),
		number("produced_quantity", "Dolumu biten", "adet", "volume", null),
		text("top_customer", "En cok alan musteri"),
		text("last_order_at", "Son siparis"));

	/** Donem karsilastirmasinda uretime ek satis ozeti. */
	static final List<Metric> PERIOD_SALES = List.of(
		number("order_count", "Siparis sayisi", "adet", "sales", true),
		number("sold_litres", "Satilan yag", "L", "sales", true),
		number("sold_quantity", "Satilan sise", "adet", "sales", true),
		number("customer_count", "Musteri sayisi", "musteri", "sales", true));

	static final List<Metric> PERIOD = Stream.concat(PRODUCTION.stream(), PERIOD_SALES.stream()).toList();

	static final List<Metric> TANK = List.of(
		number("in_litre", "Giren", "L", "flow", null),
		number("out_litre", "Cikan", "L", "flow", null),
		number("net_litre", "Net degisim", "L", "flow", null),
		number("transfer_in_litre", "Aktarmayla giren", "L", "flow", null),
		number("transfer_out_litre", "Aktarmayla cikan", "L", "flow", null),
		number("movement_count", "Hareket sayisi", "adet", "flow", null),
		number("avg_in_acidity", "Giren yagin asitligi", "%", "quality", false),
		new Metric("current_litre", "Su anki miktar", "L", "level", null, false, false),
		new Metric("fill_pct", "Doluluk", "%", "level", null, false, false));

	static final List<Metric> MACHINE = List.of(
		number("run_count", "Calisma sayisi", "adet", "work", null),
		number("batch_count", "Islenen parti", "adet", "work", null),
		number("work_hours", "Calisma suresi", "saat", "work", null),
		number("avg_run_minutes", "Ort. calisma", "dk", "work", null),
		number("processed_kg", "Islenen zeytin", "kg", "work", true),
		number("kg_per_hour", "Saatlik islenen", "kg/saat", "work", true),
		number("capacity_use_pct", "Kapasite kullanimi", "%", "work", true),
		number("yield_pct", "Randiman", "%", "yield", true),
		number("avg_acidity", "Ortalama asitlik", "%", "quality", false),
		text("last_run_at", "Son calisma"));

	private CompareMetrics() {
	}

	private static Metric number(String key, String label, String unit, String group, Boolean higherIsBetter) {
		return new Metric(key, label, unit, group, higherIsBetter, false, true);
	}

	private static Metric price(String key, String label, String unit, Boolean higherIsBetter) {
		return new Metric(key, label, unit, "cost", higherIsBetter, true, true);
	}

	private static Metric text(String key, String label) {
		return new Metric(key, label, null, "pattern", null, false, false);
	}

}
