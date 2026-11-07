package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Asamanin uzantilari ve tamamlanirken calisan isleyicilere dagitim, Laravel'deki sirayla: etkin uzantilar
 * sort_order sirasinda; her biri kendi servisine ({@code WeighingBatchProcessService},
 * {@code MaterialTransformationBatchProcessService}, {@code AnalysisBatchProcessService},
 * {@code StorageMovementBatchProcessService}).
 */
@Component
public class StageExtensions {

	private final Db db;
	private final WeighingBatchProcessService weighing;
	private final MaterialTransformationBatchProcessService transformation;
	private final AnalysisBatchProcessService analysis;
	private final StorageMovementBatchProcessService storageMovement;

	StageExtensions(Db db, WeighingBatchProcessService weighing, MaterialTransformationBatchProcessService transformation,
		AnalysisBatchProcessService analysis, StorageMovementBatchProcessService storageMovement) {
		this.db = db;
		this.weighing = weighing;
		this.transformation = transformation;
		this.analysis = analysis;
		this.storageMovement = storageMovement;
	}

	/** Asamanin uzantisi: tip anahtari, girdi kategorileri, cikti kategorileri (urun anahtariyla), degerler. */
	public record Extension(Row row, String key, boolean enabled, List<Long> inputProductIds, List<Row> outputProducts, List<Row> values) {
	}

	/** {@code productionStage.extensions} (sort_order sirasinda) ve iliskileri. */
	public List<Extension> extensions(long stageId) {
		List<Row> rows = db.whereIn("production_stage_extensions", "production_stage_id", List.of(stageId), "sort_order asc");
		List<Long> ids = Relations.ids(rows);
		Map<Long, Row> types = Relations.byId(db.whereIn("stage_extension_types", "id",
			Relations.column(rows, "stage_extension_type_id"), null));
		Map<Long, List<Row>> inputs = Relations.groupBy(db.whereIn("production_stage_extension_input_production_products",
			"production_stage_extension_id", ids, "sort_order asc"), "production_stage_extension_id");
		List<Row> outputRows = db.whereIn("production_stage_extension_output_production_products", "production_stage_extension_id",
			ids, "sort_order asc");
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(outputRows, "production_product_id"), null));
		Map<Long, List<Row>> outputs = new LinkedHashMap<>();
		for (Row o : outputRows) {
			Map<String, Object> values = new LinkedHashMap<>(o.values());
			Row product = products.get(Relations.key(o.get("production_product_id")));
			values.put("product_key", product == null ? null : product.get("key"));
			outputs.computeIfAbsent(o.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(new Row(values));
		}
		List<Row> valueRows = db.whereIn("production_stage_extension_values", "production_stage_extension_id", ids, null);
		Map<Long, Row> fields = Relations.byId(db.whereIn("stage_extension_type_fields", "id",
			Relations.column(valueRows, "stage_extension_type_field_id"), null));
		Map<Long, List<Row>> values = new LinkedHashMap<>();
		for (Row v : valueRows) {
			Map<String, Object> copy = new LinkedHashMap<>(v.values());
			Row field = fields.get(Relations.key(v.get("stage_extension_type_field_id")));
			copy.put("field_key", field == null ? null : field.get("field_key"));
			values.computeIfAbsent(v.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(new Row(copy));
		}
		List<Extension> out = new ArrayList<>();
		for (Row e : rows) {
			Row type = types.get(Relations.key(e.get("stage_extension_type_id")));
			out.add(new Extension(e, type == null ? null : Php.strval(type.get("key")), Boolean.TRUE.equals(Casts.bool(e.get("is_enabled"))),
				Relations.column(inputs.getOrDefault(e.id(), List.of()), "production_product_id"),
				outputs.getOrDefault(e.id(), List.of()), values.getOrDefault(e.id(), List.of())));
		}
		return out;
	}

	/** {@code resolveExtensionKeys}: etkin uzantilarin anahtarlari, sort_order sirasinda. */
	public static List<String> keys(List<Extension> extensions) {
		return extensions.stream().filter(Extension::enabled).map(Extension::key).filter(Objects::nonNull).toList();
	}

	/** {@code pinnedAction}: depo hareketi uzantisinda sabitlenmis tur (bos metin sabitlenmemis sayilir). */
	public static String pinnedAction(List<Extension> extensions) {
		Extension extension = StageBatchProcessService.first(extensions, "storage_movement");
		if (extension == null) {
			return null;
		}
		Row value = extension.values().stream().filter(v -> "action".equals(v.get("field_key"))).findFirst().orElse(null);
		String action = value == null || value.get("value_text") == null ? null : Php.strval(value.get("value_text")).strip();
		return action == null || action.isEmpty() ? null : action;
	}

	/** Her etkin uzantinin isleyicisi, sort_order sirasinda. {@code process} bellekteki proses. */
	public void dispatch(Row process, List<Extension> extensions, Map<String, Object> data) {
		for (String key : keys(extensions)) {
			switch (key) {
				case "weighing" -> weighing.weighing(process, extensions, data);
				case "material_transformation" -> transformation.transformation(process, extensions, data);
				case "analysis" -> analysis.analysis(process, extensions, data);
				case "storage_movement" -> storageMovement.storageMovement(process, extensions, data);
				default -> {
				}
			}
		}
	}

	/** PHP truthiness. */
	public static boolean truthy(Object value) {
		return StageBatchProcessService.truthy(value);
	}


	/** Istek dizisi uzerinde {@code $data['x']} erisimi: liste indisli dizi, skaler bos dizi gibi. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> asMap(Object value) {
		if (value instanceof Map<?, ?> m) {
			return (Map<String, Object>) m;
		}
		Map<String, Object> out = new LinkedHashMap<>();
		if (value instanceof List<?> list) {
			for (int i = 0; i < list.size(); i++) {
				out.put(String.valueOf(i), list.get(i));
			}
		}
		return out;
	}

	/**
	 * json_encode'a girecek deger PHP'deki gibi: istekteki ondalik sayilar PHP float'i ({@code 1.10 -> 1.1}).
	 */
	public static Object phpJson(Object value) {
		return switch (value) {
			case BigDecimal d -> d.doubleValue();
			case Map<?, ?> m -> {
				Map<Object, Object> out = new LinkedHashMap<>();
				m.forEach((k, v) -> out.put(k, phpJson(v)));
				yield out;
			}
			case List<?> l -> l.stream().map(StageExtensions::phpJson).toList();
			case null, default -> value;
		};
	}
}
