package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialQueries;
import com.olivamore.api.inventory.material.MaterialQueries.AttributeValueRow;
import com.olivamore.api.inventory.material.MaterialQueries.Batch;
import com.olivamore.api.inventory.material.MaterialQueries.Definition;
import com.olivamore.api.inventory.material.MaterialQueries.MaterialRow;
import com.olivamore.api.inventory.material.MaterialResponses;
import com.olivamore.api.inventory.storagemovement.StorageMovementResponse.HistoryEntry;
import com.olivamore.api.inventory.storagemovement.StorageMovementResponse.LedgerSummary;
import com.olivamore.api.inventory.storagemovement.StorageMovementResponse.MixValue;
import com.olivamore.api.inventory.storagemovement.StorageMovementResponse.Run;
import com.olivamore.api.inventory.storagemovement.StorageMovementResponse.Source;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.olivamore.api.inventory.material.MaterialResponses.lookup;

/**
 * Tank islem gecmisinin defter alanlari: yon, bakiye, gelen materyalin partisi, dolum kosusu, karsi tanktaki
 * materyal ve giristen sonraki karisim degeri.
 * <p>
 * Bakiye ve karisim saklanmaz, materyalin bu tanktaki butun hareketleri kayit (id) sirasiyla yeniden oynatilarak
 * hesaplanir (moved_at kayit ani; demo verisinde saat geri gidebildigi icin sira id ile); sayfa ya da tarih
 * siniri sonucu degistirmez. Karisim {@link TankMixAttributes} ile ayni kurala uyar: bos tanka giren degerler
 * aynen alinir, doluysa sayisal degerler tanktaki ve gelen miktara gore
 * agirlikli ortalama olur (4 basamak, integer yuvarlanir), iki taraftan birinde olmayan deger bosalir; tank analizi
 * olculen degeri yerine koyar. Ayni saniyedeki hareket ve analizde analiz sonra sayilir.
 */
@Component
public class TankLedger {

	private static final Set<MovementType> INFLOWS = EnumSet.of(MovementType.IN, MovementType.TRANSFER_IN,
		MovementType.OPENING, MovementType.ADJUSTMENT_IN);

	private static final Set<String> NUMERIC = Set.of("decimal", "integer");

	private static final int VALUE_SCALE = 4;

	private record Event(Long id, Long materialId, Long sourceMaterialId, MovementType movementType,
		BigDecimal quantity, LocalDateTime movedAt) {

		Long entered() {
			return INFLOWS.contains(movementType) ? Objects.requireNonNullElse(sourceMaterialId, materialId) : null;
		}
	}

	private record Measurement(Long id, Long materialId, Long definitionId, BigDecimal measuredValue,
		LocalDateTime measuredAt) {
	}

	private record Counterpart(Long storageUnitId, Long sourceMaterialId, Long materialId,
		String transferReference) {
	}

	private record StageRow(Long id, String name) {
	}

	private record RunRow(Long id, Long finishedProductId, Integer quantity, String status) {
	}

	private record NameRow(Long id, String name) {
	}

	/** Bir materyalin yeniden oynatilmis hareketleri: hareket id -> bakiye ve (giriste) karisim. */
	private record Replay(Map<Long, BigDecimal> balances, Map<Long, Map<Long, BigDecimal>> mixes) {
	}

	/** Tanktaki materyallerin yeniden oynatilmis butun hareketleri ve karisimdaki attribute tanimlari. */
	private record Ledger(Map<Long, BigDecimal> balances, Map<Long, Map<Long, BigDecimal>> mixes,
		Map<Long, Definition> definitions) {
	}

	private record EntryRow(Long id, Long storageUnitId, Long materialId, Long sourceMaterialId, BigDecimal quantity,
		String unit, LocalDateTime movedAt) {
	}

	/**
	 * Tanka giris aninin snapshot'i (soy agacinin tank dugumu): tank, zaman, giren miktar, giristen once ve sonra
	 * tank materyalinin bu tanktaki miktari ve giristen sonraki karisim degeri.
	 */
	public record EntrySnapshot(Long movementId, Long sourceMaterialId, Long tankMaterialId, Long storageUnitId,
		String storageUnitName, String storageUnitCode, LocalDateTime movedAt, BigDecimal quantity, String unit,
		BigDecimal balanceBefore, BigDecimal balanceAfter, List<MixValue> mixAfter) {
	}

	private record UnitRow(Long id, String name, String code) {
	}

	private final JdbcClient jdbc;
	private final MaterialQueries queries;
	private final MaterialResponses materials;

	TankLedger(JdbcClient jdbc, MaterialQueries queries, MaterialResponses materials) {
		this.jdbc = jdbc;
		this.queries = queries;
		this.materials = materials;
	}

	/**
	 * Giriste tanka giren materyal: son tankta gelen (source_material_id), on dinlendirme tankinda materyalin
	 * kendisi; cikista null.
	 */
	private static Long entered(StorageMovement row) {
		return INFLOWS.contains(row.getMovementType())
			? Objects.requireNonNullElse(row.getSourceMaterialId(), row.getMaterialId()) : null;
	}

	static String direction(MovementType type) {
		return INFLOWS.contains(type) ? "in" : "out";
	}

	/** Sayfadaki hareketlere defter alanlari; sira korunur. */
	List<HistoryEntry> entries(long unitId, List<StorageMovement> rows, List<StorageMovementResponse> responses) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Ledger ledger = ledger(unitId, rows.stream().map(StorageMovement::getMaterialId).distinct().toList());
		Map<Long, BigDecimal> balances = ledger.balances();
		Map<Long, Map<Long, BigDecimal>> mixes = ledger.mixes();
		Map<Long, Definition> definitions = ledger.definitions();

		Map<Long, MaterialRow> sources = queries.materials(column(rows, TankLedger::entered));
		Map<Long, Batch> batches = queries.batches(column(sources.values(), MaterialRow::productionBatchId));
		Map<Long, StageRow> stages = idRows("production_stages",
			column(sources.values(), MaterialRow::sourceProductionStageId), StageRow.class, StageRow::id);
		Map<Long, RunRow> runs = idRows("finished_product_runs",
			column(rows, StorageMovement::getFinishedProductRunId), RunRow.class, RunRow::id);
		Map<Long, NameRow> products = idRows("finished_products",
			column(runs.values(), RunRow::finishedProductId), NameRow.class, NameRow::id);
		List<Counterpart> counterparts = counterparts(rows.stream().map(StorageMovement::getTransferReference)
			.filter(Objects::nonNull).distinct().toList());

		List<HistoryEntry> entries = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			StorageMovement row = rows.get(i);
			boolean in = INFLOWS.contains(row.getMovementType());
			MaterialRow source = lookup(sources, entered(row));
			RunRow run = lookup(runs, row.getFinishedProductRunId());
			Map<Long, BigDecimal> mix = in ? mixes.get(row.getId()) : null;
			entries.add(new HistoryEntry(responses.get(i), direction(row.getMovementType()),
				balances.get(row.getId()), source(source, batches, stages),
				run == null ? null : new Run(run.id(), name(products, run.finishedProductId()), run.quantity(),
					run.status()),
				counterpartyMaterial(row, in, counterparts), mix == null ? null : mixValues(mix, definitions)));
		}
		return entries;
	}

	/** Giris hareketlerinin snapshot'lari: hareket id -> snapshot (giris olmayan hareketler atlanir). */
	public Map<Long, EntrySnapshot> snapshots(Collection<Long> movementIds) {
		List<Long> ids = movementIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		List<EntryRow> rows = jdbc.sql("""
			select id, storage_unit_id, material_id, source_material_id, quantity, unit, moved_at from storage_movements
			where id in (:ids) and movement_type in (:inflows)
			""").param("ids", ids).param("inflows", INFLOWS.stream().map(MovementType::value).toList())
			.query(EntryRow.class).list();
		Map<Long, UnitRow> units = idRows("storage_units", column(rows, EntryRow::storageUnitId), UnitRow.class,
			UnitRow::id);
		Map<Long, EntrySnapshot> snapshots = new HashMap<>();
		rows.stream().collect(Collectors.groupingBy(EntryRow::storageUnitId)).forEach((unitId, ofUnit) -> {
			Ledger ledger = ledger(unitId, column(ofUnit, EntryRow::materialId));
			UnitRow unit = lookup(units, unitId);
			for (EntryRow row : ofUnit) {
				BigDecimal after = ledger.balances().get(row.id());
				Map<Long, BigDecimal> mix = ledger.mixes().get(row.id());
				snapshots.put(row.id(), new EntrySnapshot(row.id(), row.sourceMaterialId(), row.materialId(), unitId,
					unit == null ? null : unit.name(), unit == null ? null : unit.code(), row.movedAt(), row.quantity(),
					row.unit(), after == null ? null : after.subtract(row.quantity()), after,
					mix == null ? null : mixValues(mix, ledger.definitions())));
			}
		});
		return snapshots;
	}

	/**
	 * Materyallerin bu tanktaki toplamlari. @param materialIds verilen materyaller; bossa tankta su an aktif
	 * olanlar
	 */
	List<LedgerSummary> summaries(long unitId, List<Long> materialIds) {
		List<Long> ids = materialIds.isEmpty() ? activeMaterials(unitId) : materialIds;
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<Long, List<Event>> events = events(unitId, ids).stream()
			.collect(Collectors.groupingBy(Event::materialId));
		Map<Long, MaterialResponse> responses = materials.materials(ids, false).stream()
			.collect(Collectors.toMap(MaterialResponse::id, Function.identity()));
		List<LedgerSummary> summaries = new ArrayList<>();
		for (Long id : ids) {
			MaterialResponse material = responses.get(id);
			if (material == null) {
				continue;
			}
			List<Event> ofMaterial = events.getOrDefault(id, List.of());
			BigDecimal totalIn = BigDecimal.ZERO;
			BigDecimal totalOut = BigDecimal.ZERO;
			int inCount = 0;
			for (Event event : ofMaterial) {
				if (INFLOWS.contains(event.movementType())) {
					totalIn = totalIn.add(event.quantity());
					inCount++;
				}
				else {
					totalOut = totalOut.add(event.quantity());
				}
			}
			summaries.add(new LedgerSummary(material, totalIn, totalOut, totalIn.subtract(totalOut), inCount,
				ofMaterial.size() - inCount, ofMaterial.isEmpty() ? null : ofMaterial.getFirst().movedAt(),
				ofMaterial.isEmpty() ? null : ofMaterial.getLast().movedAt()));
		}
		return summaries;
	}

	private Ledger ledger(long unitId, List<Long> materialIds) {
		List<Event> events = events(unitId, materialIds);
		Map<Long, Map<Long, BigDecimal>> incoming = numericValues(column(events, Event::entered));
		Map<Long, List<Measurement>> measurements = measurements(unitId, materialIds).stream()
			.collect(Collectors.groupingBy(Measurement::materialId));
		Map<Long, Definition> definitions = queries.definitions(Stream.concat(
			incoming.values().stream().flatMap(v -> v.keySet().stream()),
			measurements.values().stream().flatMap(List::stream).map(Measurement::definitionId)).distinct().toList());
		Map<Long, BigDecimal> balances = new HashMap<>();
		Map<Long, Map<Long, BigDecimal>> mixes = new HashMap<>();
		events.stream().collect(Collectors.groupingBy(Event::materialId)).forEach((materialId, ofMaterial) -> {
			Replay replay = replay(ofMaterial, measurements.getOrDefault(materialId, List.of()), incoming,
				definitions);
			balances.putAll(replay.balances());
			mixes.putAll(replay.mixes());
		});
		return new Ledger(balances, mixes, definitions);
	}

	private static Replay replay(List<Event> events, List<Measurement> measurements,
		Map<Long, Map<Long, BigDecimal>> incoming, Map<Long, Definition> definitions) {
		Map<Long, BigDecimal> balances = new HashMap<>();
		Map<Long, Map<Long, BigDecimal>> mixes = new HashMap<>();
		Map<Long, BigDecimal> mix = new LinkedHashMap<>();
		BigDecimal balance = BigDecimal.ZERO;
		int next = 0;
		List<Measurement> sorted = measurements.stream()
			.sorted(Comparator.comparing(Measurement::measuredAt).thenComparing(Measurement::id)).toList();
		for (Event event : events) {
			// once bu hareketten once yapilmis analizler (ayni saniyedeki analiz sonra sayilir)
			while (next < sorted.size() && event.movedAt() != null
				&& sorted.get(next).measuredAt().isBefore(event.movedAt())) {
				measure(mix, sorted.get(next++), definitions);
			}
			if (INFLOWS.contains(event.movementType())) {
				Map<Long, BigDecimal> values = incoming.getOrDefault(event.entered(), Map.of());
				if (balance.signum() <= 0) {
					mix.clear();
					mix.putAll(values);
				}
				else {
					merge(mix, balance, values, event.quantity(), definitions);
				}
				balance = balance.add(event.quantity());
				mixes.put(event.id(), new LinkedHashMap<>(mix));
			}
			else {
				balance = balance.subtract(event.quantity());
			}
			balances.put(event.id(), balance);
		}
		return new Replay(balances, mixes);
	}

	private static void merge(Map<Long, BigDecimal> mix, BigDecimal tankQuantity, Map<Long, BigDecimal> values,
		BigDecimal quantity, Map<Long, Definition> definitions) {
		for (Long definitionId : List.copyOf(mix.keySet())) {
			BigDecimal other = values.get(definitionId);
			if (other == null) {
				mix.remove(definitionId);
				continue;
			}
			BigDecimal mean = mix.get(definitionId).multiply(tankQuantity).add(other.multiply(quantity))
				.divide(tankQuantity.add(quantity), MathContext.DECIMAL128);
			mix.put(definitionId, stored(mean, definitions.get(definitionId)));
		}
	}

	private static void measure(Map<Long, BigDecimal> mix, Measurement measurement,
		Map<Long, Definition> definitions) {
		mix.put(measurement.definitionId(),
			stored(measurement.measuredValue(), definitions.get(measurement.definitionId())));
	}

	/** Veritabanina yazildigi gibi: integer yuvarlanir, decimal 4 basamak. */
	private static BigDecimal stored(BigDecimal value, Definition definition) {
		boolean integer = definition != null && "integer".equals(definition.dataType());
		return value.setScale(integer ? 0 : VALUE_SCALE, RoundingMode.HALF_UP);
	}

	private static List<MixValue> mixValues(Map<Long, BigDecimal> mix, Map<Long, Definition> definitions) {
		return mix.entrySet().stream().map(e -> {
			Definition definition = definitions.get(e.getKey());
			return new MixValue(e.getKey(), definition == null ? null : definition.attributeKey(),
				definition == null ? null : definition.label(), definition == null ? null : definition.unit(),
				e.getValue().stripTrailingZeros());
		}).sorted(Comparator.comparing(MixValue::definitionId)).toList();
	}

	private static Source source(MaterialRow material, Map<Long, Batch> batches, Map<Long, StageRow> stages) {
		if (material == null) {
			return null;
		}
		Batch batch = lookup(batches, material.productionBatchId());
		StageRow stage = lookup(stages, material.sourceProductionStageId());
		return new Source(material.id(), material.productionBatchId(), batch == null ? null : batch.batchCode(),
			batch == null ? null : batch.deliveryBatchCode(), stage == null ? null : stage.name());
	}

	/** Giriste gelen materyal; cikista ayni referansla karsi tanka giren hareketin tank materyali. */
	private static Long counterpartyMaterial(StorageMovement row, boolean in, List<Counterpart> counterparts) {
		if (row.getCounterpartyStorageUnitId() == null) {
			return null;
		}
		if (in) {
			return row.getSourceMaterialId();
		}
		return counterparts.stream()
			.filter(c -> Objects.equals(c.transferReference(), row.getTransferReference())
				&& Objects.equals(c.storageUnitId(), row.getCounterpartyStorageUnitId())
				&& Objects.equals(c.sourceMaterialId(), row.getMaterialId()))
			.map(Counterpart::materialId).findFirst().orElse(null);
	}

	private static String name(Map<Long, NameRow> rows, Long id) {
		NameRow row = lookup(rows, id);
		return row == null ? null : row.name();
	}

	/** Materyallerin bu tanktaki hareketleri, kayit sirasiyla (gecmis listesinin tersi). */
	private List<Event> events(long unitId, List<Long> materialIds) {
		return jdbc.sql("""
			select id, material_id, source_material_id, movement_type, quantity, moved_at from storage_movements
			where storage_unit_id = :unit and material_id in (:materials)
			order by id
			""").param("unit", unitId).param("materials", materialIds).query((rs, n) -> new Event(rs.getLong("id"),
				rs.getLong("material_id"), rs.getObject("source_material_id", Long.class),
				MovementType.valueOf(rs.getString("movement_type")), rs.getBigDecimal("quantity"),
				rs.getObject("moved_at", LocalDateTime.class))).list();
	}

	private List<Measurement> measurements(long unitId, List<Long> materialIds) {
		return jdbc.sql("""
			select id, material_id, production_product_attribute_definition_id as definition_id, measured_value,
				measured_at
			from tank_measurements where storage_unit_id = :unit and material_id in (:materials)
			""").param("unit", unitId).param("materials", materialIds).query(Measurement.class).list();
	}

	/** Gelen materyallerin sayisal attribute degerleri: materyal -> tanim -> deger. */
	private Map<Long, Map<Long, BigDecimal>> numericValues(Collection<Long> materialIds) {
		List<AttributeValueRow> rows = queries.attributeValues(materialIds);
		Map<Long, Definition> definitions = queries.definitions(column(rows, AttributeValueRow::definitionId));
		Map<Long, Map<Long, BigDecimal>> values = new HashMap<>();
		for (AttributeValueRow row : rows) {
			Definition definition = definitions.get(row.definitionId());
			BigDecimal value = row.valueDecimal() != null ? row.valueDecimal()
				: row.valueInteger() == null ? null : BigDecimal.valueOf(row.valueInteger());
			if (definition != null && NUMERIC.contains(definition.dataType()) && value != null) {
				values.computeIfAbsent(row.materialId(), k -> new HashMap<>()).put(row.definitionId(), value);
			}
		}
		return values;
	}

	private List<Counterpart> counterparts(List<String> references) {
		if (references.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
			select storage_unit_id, source_material_id, material_id, transfer_reference from storage_movements
			where transfer_reference in (:references) and source_material_id is not null
			""").param("references", references).query(Counterpart.class).list();
	}

	private List<Long> activeMaterials(long unitId) {
		return jdbc.sql("""
			select id from materials
			where current_location_type = 'storage_unit' and current_location_id = :unit and status = 'active'
			order by id
			""").param("unit", unitId).query(Long.class).list();
	}

	private <T> Map<Long, T> idRows(String table, Collection<Long> ids, Class<T> type, Function<T, Long> key) {
		List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select * from " + table + " where id in (:ids)").param("ids", present).query(type).list()
			.stream().collect(Collectors.toMap(key, Function.identity()));
	}

	private static <S> List<Long> column(Collection<S> rows, Function<S, Long> column) {
		return rows.stream().map(column).filter(Objects::nonNull).distinct().toList();
	}

}
