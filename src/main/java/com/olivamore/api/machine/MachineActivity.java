package com.olivamore.api.machine;

import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.web.Pages;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Makine sekmesi: su an calisan parcalar, son calisma, calismalar listesi, olcum serisi (grafik) ve donem ozeti.
 * Donem {@code period} (2d, 1w, 2w, 1m; simdiye kadar) ya da {@code date_from} / {@code date_to} (gun, dahil);
 * verilmezse son 1 hafta. Acik adim simdiye kadar surmus sayilir; donem disina tasan sure kirpilir.
 */
@Service
class MachineActivity {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"));
	private static final String DEFAULT_PERIOD = "1w";

	record PartRef(long id, String code, String name) {
	}

	/** Su an calisan parca ve partisi. */
	record Current(PartRef part, long batchProcessId, String batchCode, LocalDateTime startedAt) {
	}

	/** Liste ve detaydaki durum: {@code running} (en az bir parca calisiyor) ya da {@code idle}. */
	record Activity(String status, List<Current> running, LocalDateTime lastRunAt) {
	}

	record RunRow(long id, long batchProcessId, Long batchId, String batchCode, int stepOrder, PartRef part,
		LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMinutes, String note,
		List<MachineRuns.RunValue> values) {
	}

	record FieldRef(long id, String fieldKey, String label, String unit, BigDecimal minValue, BigDecimal maxValue) {
	}

	record Point(LocalDateTime at, BigDecimal value, long batchProcessId, String batchCode, boolean outOfRange) {
	}

	record PartSeries(PartRef part, List<Point> points) {
	}

	record Series(LocalDateTime dateFrom, LocalDateTime dateTo, FieldRef field, List<PartSeries> parts) {
	}

	record PartStats(PartRef part, int count, BigDecimal average, BigDecimal min, BigDecimal max) {
	}

	record FieldStats(FieldRef field, List<PartStats> parts) {
	}

	record LastRun(PartRef part, long batchProcessId, String batchCode, LocalDateTime startedAt,
		LocalDateTime finishedAt) {
	}

	/**
	 * Donem ozeti. Kullanim orani: calisma suresi / (donem suresi x aktif parca sayisi). Ezilen kg, yag ve randiman
	 * makineden gecen partilerin (her parti bir kez).
	 */
	record Summary(LocalDateTime dateFrom, LocalDateTime dateTo, LastRun lastRun, List<Current> running, int runCount,
		int batchCount, long runMinutes, BigDecimal utilisationPercent, BigDecimal pressedKg, BigDecimal kgPerHour,
		BigDecimal averageYieldPercent, BigDecimal oilKg, List<FieldStats> fields) {
	}

	private record Window(LocalDateTime from, LocalDateTime to) {

		long minutes() {
			return Duration.between(from, to).toMinutes();
		}

		/** Adimin donem icindeki suresi (dk); acik adim simdiye kadar. */
		long overlap(MachineRun run, LocalDateTime now) {
			LocalDateTime end = run.getFinishedAt() == null ? now : run.getFinishedAt();
			LocalDateTime start = run.getStartedAt().isBefore(from) ? from : run.getStartedAt();
			LocalDateTime stop = end.isAfter(to) ? to : end;
			return stop.isAfter(start) ? Duration.between(start, stop).toMinutes() : 0;
		}

	}

	private final MachineRepository machines;
	private final MachineRunRepository runs;
	private final MachineBatches batches;

	MachineActivity(MachineRepository machines, MachineRunRepository runs, MachineBatches batches) {
		this.machines = machines;
		this.runs = runs;
		this.batches = batches;
	}

	/** Makinelerin su anki durumu ve son calisma ani. */
	Map<Long, Activity> of(Collection<Long> machineIds) {
		if (machineIds.isEmpty()) {
			return Map.of();
		}
		List<MachineRun> open = runs.findOpenOfMachines(machineIds);
		Map<Long, MachineBatches.Batch> batchOf = batches.batches(open.stream().map(MachineRun::getBatchProcessId)
			.distinct().toList());
		Map<Long, LocalDateTime> last = runs.lastStarts(machineIds).stream().collect(Collectors.toMap(
			MachineRunRepository.LastStart::getMachineId, MachineRunRepository.LastStart::getStartedAt));
		Map<Long, Activity> result = new LinkedHashMap<>();
		for (Long id : machineIds) {
			List<Current> running = open.stream().filter(r -> r.getMachinePart().getMachine().getId().equals(id))
				.map(r -> new Current(part(r.getMachinePart()), r.getBatchProcessId(),
					code(batchOf, r.getBatchProcessId()), r.getStartedAt()))
				.toList();
			result.put(id, new Activity(running.isEmpty() ? "idle" : "running", running, last.get(id)));
		}
		return result;
	}

	/** {@code date_from}, {@code date_to}: baslangic gunu araligi; en yeni once, {@code page}/{@code size} varsa sayfali. */
	@Transactional(readOnly = true)
	PagedModel<RunRow> runs(long machineId, String dateFrom, String dateTo, Pageable pageable) {
		find(machineId);
		LocalDate from = date("date_from", dateFrom);
		LocalDate to = date("date_to", dateTo);
		Specification<MachineRun> filter = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("machinePart").get("machine").get("id"), machineId));
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from.atStartOfDay()));
			}
			if (to != null) {
				predicates.add(cb.lessThan(root.get("startedAt"), to.plusDays(1).atStartOfDay()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
		return Pages.of(pageable, requested -> {
			Page<MachineRun> page = runs.findAll(filter, Pages.sorted(requested, NEWEST_FIRST));
			return new PageImpl<>(rows(page.getContent()), page.getPageable(), page.getTotalElements());
		}, () -> rows(runs.findAll(filter, NEWEST_FIRST)));
	}

	/** Sayisal olcum alaninin zaman serisi (adimin baslangic ani), parca basina. */
	@Transactional(readOnly = true)
	Series series(long machineId, String fieldKey, String period, String dateFrom, String dateTo) {
		Machine machine = find(machineId);
		MachineTypeField field = machine.getMachineType().getFields().stream()
			.filter(f -> !f.forMachine() && f.numeric() && f.getFieldKey().equals(fieldKey)).findFirst()
			.orElseThrow(() -> ValidationFailedException.of("field", MessageKeys.IN));
		Window window = window(period, dateFrom, dateTo);
		List<MachineRun> inWindow = runs.findInWindow(machineId, window.from(), window.to());
		Map<Long, MachineBatches.Batch> batchOf = batchesOf(inWindow);
		List<PartSeries> parts = new ArrayList<>();
		for (MachinePart part : machine.getParts()) {
			List<Point> points = inWindow.stream().filter(r -> r.getMachinePart().getId().equals(part.getId()))
				.flatMap(r -> value(r, field).map(v -> new Point(r.getStartedAt(), v, r.getBatchProcessId(),
					code(batchOf, r.getBatchProcessId()), MachineFieldValues.outOfRange(field, number(v)))).stream())
				.toList();
			parts.add(new PartSeries(part(part), points));
		}
		return new Series(window.from(), window.to(), fieldRef(field), parts);
	}

	@Transactional(readOnly = true)
	Summary summary(long machineId, String period, String dateFrom, String dateTo) {
		Machine machine = find(machineId);
		Window window = window(period, dateFrom, dateTo);
		LocalDateTime now = Timestamps.now();
		List<MachineRun> inWindow = runs.findInWindow(machineId, window.from(), window.to());
		Map<Long, MachineBatches.Batch> batchOf = batchesOf(inWindow);

		long runMinutes = inWindow.stream().mapToLong(r -> window.overlap(r, now)).sum();
		long activeParts = Math.max(1, machine.getParts().stream().filter(MachinePart::isActive).count());
		BigDecimal utilisation = window.minutes() <= 0 ? null : BigDecimal.valueOf(runMinutes * 100)
			.divide(BigDecimal.valueOf(window.minutes() * activeParts), 1, RoundingMode.HALF_UP);
		List<MachineBatches.Batch> passed = inWindow.stream().map(MachineRun::getBatchProcessId).distinct()
			.map(batchOf::get).filter(Objects::nonNull).toList();
		BigDecimal pressedKg = sum(passed.stream().map(MachineBatches.Batch::pressedKg).toList());
		BigDecimal kgPerHour = pressedKg == null || runMinutes == 0 ? null
			: pressedKg.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(runMinutes), 1, RoundingMode.HALF_UP);
		List<BigDecimal> yields = passed.stream().map(MachineBatches.Batch::yieldPercent).filter(Objects::nonNull)
			.toList();
		BigDecimal averageYield = yields.isEmpty() ? null : yields.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
			.divide(BigDecimal.valueOf(yields.size()), 2, RoundingMode.HALF_UP);

		Activity activity = of(List.of(machineId)).get(machineId);
		LastRun lastRun = runs.findFirstByMachinePartMachineIdOrderByStartedAtDescIdDesc(machineId)
			.map(r -> new LastRun(part(r.getMachinePart()), r.getBatchProcessId(),
				code(batches.batches(List.of(r.getBatchProcessId())), r.getBatchProcessId()), r.getStartedAt(),
				r.getFinishedAt()))
			.orElse(null);

		return new Summary(window.from(), window.to(), lastRun, activity.running(), inWindow.size(), passed.size(),
			runMinutes, utilisation, pressedKg, kgPerHour, averageYield,
			sum(passed.stream().map(MachineBatches.Batch::oilKg).toList()), fieldStats(machine, inWindow));
	}

	/** Sayisal olcum alanlari (aktif ya da donemde degeri olan) icin parca basina sayi, ortalama, en kucuk, en buyuk. */
	private static List<FieldStats> fieldStats(Machine machine, List<MachineRun> inWindow) {
		List<FieldStats> result = new ArrayList<>();
		List<MachineTypeField> fields = machine.getMachineType().getFields().stream()
			.filter(f -> !f.forMachine() && f.numeric())
			.sorted(Comparator.comparingInt(MachineTypeField::getSortOrder)).toList();
		for (MachineTypeField field : fields) {
			List<PartStats> parts = new ArrayList<>();
			for (MachinePart part : machine.getParts()) {
				List<BigDecimal> values = inWindow.stream()
					.filter(r -> r.getMachinePart().getId().equals(part.getId()))
					.flatMap(r -> value(r, field).stream()).toList();
				if (!values.isEmpty()) {
					BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
					parts.add(new PartStats(part(part), values.size(),
						total.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP),
						values.stream().min(Comparator.naturalOrder()).orElseThrow(),
						values.stream().max(Comparator.naturalOrder()).orElseThrow()));
				}
			}
			if (field.isActive() || !parts.isEmpty()) {
				result.add(new FieldStats(fieldRef(field), parts));
			}
		}
		return result;
	}

	private static Optional<BigDecimal> value(MachineRun run, MachineTypeField field) {
		return run.getValues().stream().filter(v -> v.getField().getId().equals(field.getId())).findFirst()
			.map(v -> v.getValueDecimal() != null ? v.getValueDecimal().stripTrailingZeros()
				: v.getValueInteger() == null ? null : BigDecimal.valueOf(v.getValueInteger()));
	}

	private List<RunRow> rows(List<MachineRun> page) {
		Map<Long, MachineBatches.Batch> batchOf = batchesOf(page);
		return page.stream().map(r -> {
			MachineBatches.Batch batch = batchOf.get(r.getBatchProcessId());
			return new RunRow(r.getId(), r.getBatchProcessId(), batch == null ? null : batch.batchId(),
				batch == null ? null : batch.batchCode(), r.getStepOrder(), part(r.getMachinePart()),
				r.getStartedAt(), r.getFinishedAt(),
				r.getFinishedAt() == null ? null : Duration.between(r.getStartedAt(), r.getFinishedAt()).toMinutes(),
				r.getNote(), r.getValues().stream().map(v -> new MachineRuns.RunValue(v.getField().getId(),
					v.getField().getFieldKey(), v.getField().getLabel(), v.getField().getUnit(), v.json(),
					MachineFieldValues.outOfRange(v.getField(), v.json()))).toList());
		}).toList();
	}

	private Map<Long, MachineBatches.Batch> batchesOf(List<MachineRun> list) {
		return batches.batches(list.stream().map(MachineRun::getBatchProcessId).distinct().toList());
	}

	/**
	 * Donem (sinirlar dahil): tarih araligi verildiyse o (gunler dahil), yoksa hazir donem (simdi dahil; adim simdi
	 * baslamis olabilir).
	 */
	private static Window window(String period, String dateFrom, String dateTo) {
		LocalDateTime now = Timestamps.now();
		LocalDate from = date("date_from", dateFrom);
		LocalDate to = date("date_to", dateTo);
		if (from != null || to != null) {
			// Saatler saniye hassasiyetinde; son gunun son saniyesi dahil
			LocalDateTime end = to == null ? now : to.plusDays(1).atStartOfDay().minusSeconds(1);
			LocalDateTime start = from == null ? end.minusWeeks(1) : from.atStartOfDay();
			if (!end.isAfter(start)) {
				throw ValidationFailedException.of("date_to", MessageKeys.IN);
			}
			return new Window(start, end);
		}
		String chosen = period == null || period.isBlank() ? DEFAULT_PERIOD : period.strip();
		LocalDateTime start = switch (chosen) {
			case "2d" -> now.minusDays(2);
			case "1w" -> now.minusWeeks(1);
			case "2w" -> now.minusWeeks(2);
			case "1m" -> now.minusMonths(1);
			default -> throw ValidationFailedException.of("period", MessageKeys.IN);
		};
		return new Window(start, now);
	}

	private static LocalDate date(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.strip());
		}
		catch (DateTimeParseException e) {
			throw ValidationFailedException.of(field, MessageKeys.DATE);
		}
	}

	private static BigDecimal sum(List<BigDecimal> values) {
		List<BigDecimal> known = values.stream().filter(Objects::nonNull).toList();
		return known.isEmpty() ? null : known.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static String code(Map<Long, MachineBatches.Batch> batchOf, long batchProcessId) {
		MachineBatches.Batch batch = batchOf.get(batchProcessId);
		return batch == null ? null : batch.batchCode();
	}

	private static JsonNode number(BigDecimal value) {
		return JsonNodeFactory.instance.numberNode(value);
	}

	private static PartRef part(MachinePart part) {
		return new PartRef(part.getId(), part.getCode(), part.getName());
	}

	private static FieldRef fieldRef(MachineTypeField field) {
		return new FieldRef(field.getId(), field.getFieldKey(), field.getLabel(), field.getUnit(),
			field.getMinValue(), field.getMaxValue());
	}

	private Machine find(long id) {
		return machines.findById(id).orElseThrow(() -> new ModelNotFoundException("Machine", id));
	}

}
