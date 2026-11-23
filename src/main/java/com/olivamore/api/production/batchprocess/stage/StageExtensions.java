package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Asamanin etkin uzantilarini sort_order sirasinda kendi isleyicilerine dagitir. */
@Component
public class StageExtensions {

	private final WeighingBatchProcessService weighing;
	private final MaterialTransformationBatchProcessService transformation;
	private final AnalysisBatchProcessService analysis;
	private final StorageMovementBatchProcessService storageMovement;
	private final PreSettlingBatchProcessService preSettling;
	private final YieldBatchProcessService yield;

	StageExtensions(WeighingBatchProcessService weighing, MaterialTransformationBatchProcessService transformation,
		AnalysisBatchProcessService analysis, StorageMovementBatchProcessService storageMovement,
		PreSettlingBatchProcessService preSettling, YieldBatchProcessService yield) {
		this.weighing = weighing;
		this.transformation = transformation;
		this.analysis = analysis;
		this.storageMovement = storageMovement;
		this.preSettling = preSettling;
		this.yield = yield;
	}

	/**
	 * Partinin randiman hesabi (kg): sikima giren zeytin, son tanka giden yag, oran (%); bilinmeyen null.
	 */
	public record YieldCalculation(BigDecimal oliveKg, BigDecimal filteredOilKg, BigDecimal yieldPercent) {
	}

	public YieldCalculation yieldOf(long batchId) {
		YieldBatchProcessService.Calculation c = yield.calculate(batchId);
		return new YieldCalculation(c.oliveKg(), c.filteredOilKg(), c.yieldPercent());
	}

	/** Etkin uzantilarin anahtarlari, sort_order sirasinda. */
	public static List<String> keys(List<Extension> extensions) {
		return extensions.stream().filter(Extension::enabled).map(Extension::key).filter(Objects::nonNull).toList();
	}

	/** Tanka urun sokan uzanti var mi (depo hareketi ya da on dinlendirme): asama onaya duser. */
	public static boolean movesStock(List<Extension> extensions) {
		return keys(extensions).stream().anyMatch(key -> "storage_movement".equals(key) || "pre_settling".equals(key));
	}

	/** Kronometre uzantisi varsa makine Baslat/Bitir ile olculur; bitmeden asama tamamlanmaz. */
	public static boolean timed(List<Extension> extensions) {
		return keys(extensions).contains("timer");
	}

	/** On dinlendirme uzantisi varsa asama tamamlanmaz, tank bosaltilana kadar dinlenmede bekler. */
	public static boolean rests(List<Extension> extensions) {
		return keys(extensions).contains("pre_settling");
	}

	/** Depo hareketi uzantisinda sabitlenmis tur (bos metin sabitlenmemis sayilir). */
	public static String pinnedAction(List<Extension> extensions) {
		Extension extension = StageBatchProcessService.first(extensions, "storage_movement");
		if (extension == null) {
			return null;
		}
		String action = extension.values().stream().filter(v -> "action".equals(v.fieldKey())).findFirst()
			.map(v -> v.valueText() == null ? null : v.valueText().strip()).orElse(null);
		return action == null || action.isEmpty() ? null : action;
	}

	/**
	 * Makine hatti uzantisinin makine sirasi (tip anahtarlari); uzanti yoksa {@code null}. Ayar
	 * {@code machine_types}: ["kirici", "malaksor", ...].
	 */
	public static List<String> machineRoute(List<Extension> extensions) {
		Extension extension = StageBatchProcessService.first(extensions, "machine_route");
		if (extension == null) {
			return null;
		}
		JsonNode types = extension.values().stream().filter(v -> "machine_types".equals(v.fieldKey())).findFirst()
			.map(StageQueries.ExtensionValue::valueJson).orElse(null);
		List<String> keys = new ArrayList<>();
		if (types != null && types.isArray()) {
			types.forEach(node -> {
				if (node.isString() && !node.asString().isBlank()) {
					keys.add(node.asString().strip());
				}
			});
		}
		return keys;
	}

	public void dispatch(StageProcess process, List<Extension> extensions, OperationData data) {
		for (String key : keys(extensions)) {
			switch (key) {
				case "weighing" -> weighing.weighing(process, extensions, data);
				case "material_transformation" -> transformation.transformation(process, extensions, data);
				case "analysis" -> analysis.analysis(process, extensions, data);
				case "storage_movement" -> storageMovement.storageMovement(process, extensions, data);
				case "pre_settling" -> preSettling.preSettling(process, extensions, data);
				case "yield" -> yield.yield(process, extensions, data);
				default -> {
				}
			}
		}
	}

	/** Teslimatin kantar tartimi geri alinir: yarattigi materyal silinir. */
	public void undoWeighing(long batchProcessId) {
		weighing.undo(batchProcessId);
	}

	/** Dinlenmedeki asamanin iptali: tanktaki urun partiye doner. */
	public void cancelPreSettling(long batchProcessId) {
		preSettling.cancel(batchProcessId);
	}

	/** Bosaltilan on dinlendirme tankindaki parti materyalinin tortusu (kg); sifirsa materyal acilmaz. */
	public void residue(StageProcess process, List<Extension> extensions, long materialId, BigDecimal residueKg) {
		preSettling.residue(process, extensions, materialId, residueKg);
	}

}
