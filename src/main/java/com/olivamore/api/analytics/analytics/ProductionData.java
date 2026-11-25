package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsQueries.BatchRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.BatchTotals;
import com.olivamore.api.analytics.analytics.CompareQueries.Delivery;
import com.olivamore.api.analytics.analytics.CompareQueries.IasValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.olivamore.api.analytics.analytics.AnalyticsService.round;

/**
 * Uretim gruplarinin ortak verisi: araliktaki partiler (analizdeki {@link AnalyticsQueries#batches} ile, gruplara
 * bolunmus teslimat yerine gruplari), teslimatlar ve IAS degerleri. Degerler {@link #values}: hacim ve verim
 * {@link AnalyticsService#totals} ile (tedarikci listesiyle ayni), IAS parti kilosuyla agirlikli, alim kg ile
 * agirlikli.
 *
 * @param deliveryOf parti -> teslimati
 */
record ProductionData(List<BatchRow> rows, List<Delivery> deliveries, Map<Long, Delivery> deliveryOf,
	Map<Long, Map<String, Double>> ias) {

	static ProductionData load(AnalyticsQueries analytics, CompareQueries queries, Range range, Long productTypeId,
		Long supplierId, Long fieldId) {
		List<BatchRow> rows = analytics.batches(range, supplierId, productTypeId, fieldId);
		List<Delivery> deliveries = queries.deliveries(range, productTypeId).stream()
			.filter(d -> supplierId == null || supplierId.equals(d.supplierId()))
			.filter(d -> fieldId == null || fieldId.equals(d.supplierFieldId()))
			.toList();
		List<Long> batchIds = rows.stream().map(BatchRow::batchId).toList();
		Map<Long, Delivery> byId = deliveries.stream().collect(Collectors.toMap(Delivery::id, Function.identity()));
		Map<Long, Delivery> deliveryOf = new HashMap<>();
		queries.deliveryIds(batchIds).forEach((batch, delivery) -> {
			if (byId.containsKey(delivery)) {
				deliveryOf.put(batch, byId.get(delivery));
			}
		});
		Map<Long, Map<String, Double>> ias = new HashMap<>();
		for (IasValue value : queries.iasValues(batchIds)) {
			ias.computeIfAbsent(value.batchId(), k -> new HashMap<>())
				.put(value.productKey() + "." + value.attributeKey(), value.valueDecimal().doubleValue());
		}
		return new ProductionData(rows, deliveries, deliveryOf, ias);
	}

	/** Kosula uyan partiler ve teslimatlarin degerleri. */
	Map<String, Object> values(Predicate<BatchRow> row, Predicate<Delivery> delivery) {
		return values(rows.stream().filter(row).toList(), deliveries.stream().filter(delivery).toList());
	}

	/** Parti ve teslimat zamanina gore suzgec. */
	static Predicate<BatchRow> rowAt(Predicate<LocalDateTime> when) {
		return r -> when.test(r.receivedAt());
	}

	static Predicate<Delivery> deliveryAt(Predicate<LocalDateTime> when) {
		return d -> when.test(d.receivedAt());
	}

	private Map<String, Object> values(List<BatchRow> rows, List<Delivery> deliveries) {
		Map<String, Object> v = new HashMap<>();
		BatchTotals totals = AnalyticsService.totals(rows);
		v.put("delivery_count", deliveries.size());
		v.put("incoming_kg", totals.incomingKg());
		v.put("pressed_kg", totals.pressedKg());
		v.put("oil_kg", totals.oilKg());
		v.put("yield_pct", totals.yieldPct());
		v.put("gross_yield_pct", totals.grossYieldPct());
		v.put("pomace_ratio", totals.pressedKg() > 0 ? round(totals.pomaceKg() / totals.pressedKg() * 100, 2) : null);
		v.put("residue_ratio", totals.oilKg() > 0 ? round(totals.residueKg() / totals.oilKg() * 100, 2) : null);
		v.put("avg_acidity", totals.avgAcidity());
		v.put("olive_oil_ratio", weighted(rows, "olive.oil_ratio", BatchRow::incoming));
		v.put("olive_kpy", weighted(rows, "olive.kpy", BatchRow::incoming));
		v.put("industrial_yield", weighted(rows, "olive.industrial_yield", BatchRow::incoming));
		v.put("pomace_kpy", weighted(rows, "pomace.kpy", BatchRow::pomace));
		purchase(v, deliveries);
		v.put("last_delivery_at", deliveries.stream().map(Delivery::receivedAt).filter(Objects::nonNull)
			.max(Comparator.naturalOrder()).orElse(null));
		double deliveredKg = deliveries.stream().map(Delivery::quantityKg).filter(Objects::nonNull)
			.mapToDouble(BigDecimal::doubleValue).sum();
		v.put("avg_delivery_kg", deliveries.isEmpty() ? null : round(deliveredKg / deliveries.size(), 1));
		return v;
	}

	/** Fiyatli teslimatlar: kg agirlikli fiyat; maliyet nakliyeyle; en sik kanal. */
	private static void purchase(Map<String, Object> v, List<Delivery> deliveries) {
		double kg = 0;
		double goods = 0;
		double goodsWithVat = 0;
		double transport = 0;
		for (Delivery d : deliveries) {
			if (d.pricePerKg() == null || d.quantityKg() == null || d.quantityKg().signum() <= 0) {
				continue;
			}
			double q = d.quantityKg().doubleValue();
			double price = d.pricePerKg().doubleValue();
			double vat = d.vatRate() == null ? 0 : d.vatRate().doubleValue();
			kg += q;
			goods += q * price;
			goodsWithVat += q * price * (1 + vat / 100);
			transport += d.transportCost() == null ? 0 : d.transportCost().doubleValue();
		}
		v.put("avg_price_per_kg", kg > 0 ? round(goods / kg, 2) : null);
		v.put("avg_price_per_kg_with_vat", kg > 0 ? round(goodsWithVat / kg, 2) : null);
		v.put("cost_per_kg", kg > 0 ? round((goods + transport) / kg, 2) : null);
		v.put("purchase_total", kg > 0 ? round(goods + transport, 2) : null);
		v.put("main_channel", deliveries.stream().map(Delivery::supplyChannel).filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
			.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null));
	}

	/** Partilerin IAS degeri, partinin kilosuyla agirlikli (kilo yoksa 1). */
	private Double weighted(List<BatchRow> rows, String key, Function<BatchRow, Double> weight) {
		double sum = 0;
		double weights = 0;
		for (BatchRow row : rows) {
			Double value = ias.getOrDefault(row.batchId(), Map.of()).get(key);
			if (value == null) {
				continue;
			}
			Double w = weight.apply(row);
			double ww = w == null || w <= 0 ? 1 : w;
			sum += value * ww;
			weights += ww;
		}
		return weights > 0 ? round(sum / weights, 2) : null;
	}

}
