package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsQueries.BatchRow;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.BatchTotals;
import com.olivamore.api.analytics.analytics.CompareQueries.MachineRun;
import com.olivamore.api.analytics.analytics.CompareQueries.Movement;
import com.olivamore.api.analytics.analytics.CompareQueries.TankState;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.olivamore.api.analytics.analytics.AnalyticsService.round;

/**
 * Tesis gruplari: tanklar (tank hareketlerinden; giren yagin asitligi materyalden, doluluk su anki bakiyeden) ve
 * makineler (makine calismalarindan; islenen zeytin ve randiman calismanin partilerinden).
 */
final class FacilitySubject implements CompareSubject {

	private static final Set<String> IN = Set.of("IN", "TRANSFER_IN");
	private static final Set<String> OUT = Set.of("OUT", "TRANSFER_OUT");

	private final boolean tanks;
	private final AnalyticsQueries analytics;
	private final CompareQueries queries;

	private FacilitySubject(boolean tanks, AnalyticsQueries analytics, CompareQueries queries) {
		this.tanks = tanks;
		this.analytics = analytics;
		this.queries = queries;
	}

	static List<CompareSubject> all(AnalyticsQueries analytics, CompareQueries queries) {
		return List.of(new FacilitySubject(true, analytics, queries), new FacilitySubject(false, analytics, queries));
	}

	@Override
	public String key() {
		return tanks ? "tank" : "machine";
	}

	@Override
	public String label() {
		return tanks ? "Tanklar" : "Makineler";
	}

	@Override
	public List<Metric> metrics() {
		return tanks ? CompareMetrics.TANK : CompareMetrics.MACHINE;
	}

	@Override
	public boolean filtersProductType() {
		return !tanks;
	}

	@Override
	public List<Item> options(Context context) {
		return queries.all(key());
	}

	@Override
	public List<Item> items(List<String> ids) {
		return queries.names(key(), CompareSubject.longs(ids));
	}

	@Override
	public Values load(Context context, List<Item> items) {
		List<Long> ids = CompareSubject.longs(items.stream().map(Item::id).toList());
		return tanks ? tankValues(context, ids) : machineValues(context, ids);
	}

	// ---- tanklar --------------------------------------------------------------------------------

	private Values tankValues(Context context, List<Long> ids) {
		List<Movement> movements = queries.movements(context.range(), ids).stream()
			.filter(m -> m.litres() != null).toList();
		Map<Long, Double> acidity = queries.acidity(movements.stream().filter(m -> IN.contains(m.movementType()))
			.map(Movement::materialId).distinct().toList());
		Map<Long, TankState> states = queries.tankStates(ids).stream()
			.collect(Collectors.toMap(TankState::id, Function.identity()));
		return (item, when) -> {
			long tank = Long.parseLong(item.id());
			List<Movement> mine = movements.stream()
				.filter(m -> m.storageUnitId() == tank && m.movedAt() != null && when.test(m.movedAt())).toList();
			Map<String, Object> v = new HashMap<>();
			double in = sum(mine, m -> IN.contains(m.movementType()));
			double out = sum(mine, m -> OUT.contains(m.movementType()));
			v.put("in_litre", round(in, 2));
			v.put("out_litre", round(out, 2));
			v.put("net_litre", round(in - out, 2));
			v.put("transfer_in_litre", round(sum(mine, m -> "TRANSFER_IN".equals(m.movementType())), 2));
			v.put("transfer_out_litre", round(sum(mine, m -> "TRANSFER_OUT".equals(m.movementType())), 2));
			v.put("movement_count", mine.size());
			double weighted = 0;
			double litres = 0;
			for (Movement m : mine) {
				Double value = acidity.get(m.materialId());
				if (IN.contains(m.movementType()) && value != null) {
					weighted += value * m.litres().doubleValue();
					litres += m.litres().doubleValue();
				}
			}
			v.put("avg_in_acidity", litres > 0 ? round(weighted / litres, 3) : null);
			TankState state = states.get(tank);
			double current = state == null || state.current() == null ? 0 : state.current().doubleValue();
			v.put("current_litre", round(current, 2));
			v.put("fill_pct", state == null || state.capacity() == null || state.capacity().signum() <= 0 ? null
				: round(current / state.capacity().doubleValue() * 100, 1));
			return v;
		};
	}

	private static double sum(List<Movement> movements, Predicate<Movement> which) {
		return movements.stream().filter(which).mapToDouble(m -> m.litres().doubleValue()).sum();
	}

	// ---- makineler ------------------------------------------------------------------------------

	private Values machineValues(Context context, List<Long> ids) {
		List<MachineRun> runs = queries.machineRuns(context.range(), ids);
		Map<Long, BatchRow> rows = analytics.batches(context.range(), null, context.productTypeId(), null).stream()
			.collect(Collectors.toMap(BatchRow::batchId, Function.identity()));
		return (item, when) -> {
			long machine = Long.parseLong(item.id());
			List<MachineRun> mine = runs.stream()
				.filter(r -> r.machineId() == machine && when.test(r.startedAt()))
				.filter(r -> context.productTypeId() == null || rows.containsKey(r.batchId()))
				.toList();
			Map<String, Object> v = new HashMap<>();
			double minutes = mine.stream().filter(r -> r.finishedAt() != null)
				.mapToDouble(r -> Duration.between(r.startedAt(), r.finishedAt()).toMinutes()).sum();
			long finished = mine.stream().filter(r -> r.finishedAt() != null).count();
			List<BatchRow> batches = mine.stream().map(MachineRun::batchId).distinct().map(rows::get)
				.filter(Objects::nonNull).toList();
			BatchTotals totals = AnalyticsService.totals(batches);
			double hours = minutes / 60;
			v.put("run_count", mine.size());
			v.put("batch_count", mine.stream().map(MachineRun::batchId).distinct().count());
			v.put("work_hours", round(hours, 1));
			v.put("avg_run_minutes", finished > 0 ? round(minutes / finished, 1) : null);
			v.put("processed_kg", totals.pressedKg());
			Double perHour = hours > 0 && totals.pressedKg() > 0 ? round(totals.pressedKg() / hours, 1) : null;
			v.put("kg_per_hour", perHour);
			Double capacity = mine.stream().map(MachineRun::capacityKgPerHour).filter(Objects::nonNull).findFirst()
				.map(c -> c.doubleValue()).orElse(null);
			v.put("capacity_use_pct", perHour == null || capacity == null || capacity <= 0 ? null
				: round(perHour / capacity * 100, 1));
			v.put("yield_pct", batches.isEmpty() ? null : totals.yieldPct());
			v.put("avg_acidity", totals.avgAcidity());
			v.put("last_run_at", mine.stream().map(MachineRun::startedAt).max(Comparator.naturalOrder())
				.orElse(null));
			return v;
		};
	}

}
