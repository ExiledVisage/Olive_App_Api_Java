package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Asama tamamlanirken {@code operation_data}'nin sekli, asamanin etkin uzantilarina gore. Her uzantinin alanlari
 * sort_order sirasinda; ayni alan iki uzantida varsa ilki gecerli.
 */
@Component
public class OperationDataRules {

	private static final String ROOT = "operation_data";
	private static final String PRODUCT_TYPES = "production_product_types";
	private static final String MATERIALS = "materials";
	private static final String UNITS = "storage_units";

	/** Uzantilarin tanimli alanlari; digerleri saklanmaz. */
	private static final Map<String, List<String>> FIELDS = Map.of(
		"weighing", List.of("quantity", "unit", "production_product_type_id"),
		"material_transformation", List.of("input_quantity", "input_material_id", "input_production_product_type_id", "outputs"),
		"analysis", List.of("material_id", "attributes"),
		"storage_movement", List.of("action", "material_id", "quantity", "note", "storage_unit_id", "from_storage_unit_id",
			"to_storage_unit_id"),
		"pre_settling", List.of("material_id", "quantity", "note", "storage_unit_id"));

	private static final Map<String, List<String>> ROW_FIELDS = Map.of(
		"outputs", List.of("quantity", "unit", "production_product_type_id", "production_product_id", "product_type_key"),
		"attributes", List.of("attribute_key", "production_product_attribute_definition_id", "value", "value_boolean",
			"value_integer", "value_decimal", "value_text", "value_json"));

	private final StageQueries queries;

	OperationDataRules(StageQueries queries) {
		this.queries = queries;
	}

	/** Uzanti yoksa (ya da tamamlama degilse) {@code operation_data} serbest bir dizi. */
	public void checkOptional(ValidationErrors errors, JsonNode data) {
		if (OperationData.isPresent(data) && !data.isContainer()) {
			errors.add(ROOT, MessageKeys.ARRAY);
		}
	}

	public void check(ValidationErrors errors, List<Extension> extensions, JsonNode data) {
		Check c = new Check(errors, data);
		Map<String, Runnable> rules = new LinkedHashMap<>();
		for (String key : StageExtensions.keys(extensions)) {
			switch (key) {
				case "weighing" -> weighing(rules, c);
				case "material_transformation" -> transformation(rules, c);
				case "analysis" -> analysis(rules, c);
				case "storage_movement" -> storageMovement(rules, c, extensions);
				case "pre_settling" -> preSettling(rules, c);
				default -> {
				}
			}
		}
		if (rules.isEmpty()) {
			checkOptional(errors, data);
			return;
		}
		c.root();
		rules.values().forEach(Runnable::run);
	}

	/** Uzantilarin tanimlamadigi alanlar atilir; uzanti yoksa veri oldugu gibi kalir. */
	public JsonNode declaredOnly(List<Extension> extensions, JsonNode data) {
		Set<String> fields = new LinkedHashSet<>();
		StageExtensions.keys(extensions).forEach(key -> fields.addAll(FIELDS.getOrDefault(key, List.of())));
		if (fields.isEmpty() || data == null || !data.isContainer()) {
			return data;
		}
		ObjectNode out = JsonNodeFactory.instance.objectNode();
		for (String field : fields) {
			if (data.has(field)) {
				out.set(field, ROW_FIELDS.containsKey(field) ? rows(data.get(field), ROW_FIELDS.get(field)) : data.get(field));
			}
		}
		return out;
	}

	private static JsonNode rows(JsonNode rows, List<String> fields) {
		if (rows.isArray()) {
			ArrayNode out = JsonNodeFactory.instance.arrayNode();
			rows.values().forEach(row -> out.add(row(row, fields)));
			return out;
		}
		if (rows.isObject()) {
			ObjectNode out = JsonNodeFactory.instance.objectNode();
			rows.properties().forEach(e -> out.set(e.getKey(), row(e.getValue(), fields)));
			return out;
		}
		return rows;
	}

	private static JsonNode row(JsonNode row, List<String> fields) {
		ObjectNode out = JsonNodeFactory.instance.objectNode();
		fields.forEach(field -> {
			if (row.has(field)) {
				out.set(field, row.get(field));
			}
		});
		return out;
	}

	private static void weighing(Map<String, Runnable> rules, Check c) {
		rules.putIfAbsent("quantity", () -> c.positive("quantity"));
		rules.putIfAbsent("unit", () -> c.unit("unit"));
		rules.putIfAbsent("production_product_type_id", () -> c.id("production_product_type_id", PRODUCT_TYPES));
	}

	private static void transformation(Map<String, Runnable> rules, Check c) {
		rules.putIfAbsent("input_quantity", () -> c.positive("input_quantity"));
		rules.putIfAbsent("input_material_id", () -> c.id("input_material_id", MATERIALS));
		rules.putIfAbsent("input_production_product_type_id", () -> c.id("input_production_product_type_id", PRODUCT_TYPES));
		rules.putIfAbsent("outputs", () -> c.requiredList("outputs"));
		rules.putIfAbsent("outputs.*", () -> c.itemsAreArrays("outputs"));
		rules.putIfAbsent("outputs.*.quantity", () -> c.eachItem("outputs", "quantity",
			(node, path) -> c.positiveAt(node, path)));
		rules.putIfAbsent("outputs.*.unit", () -> c.eachItem("outputs", "unit",
			(node, path) -> c.unitAt(node, path)));
		rules.putIfAbsent("outputs.*.production_product_type_id", () -> c.outputType());
		rules.putIfAbsent("outputs.*.production_product_id", () -> c.eachItem("outputs", "production_product_id",
			(node, path) -> c.idAt(node, path, "production_products")));
		rules.putIfAbsent("outputs.*.product_type_key", () -> c.eachItem("outputs", "product_type_key",
			(node, path) -> c.stringAt(node, path)));
	}

	private static void analysis(Map<String, Runnable> rules, Check c) {
		String definition = "production_product_attribute_definition_id";
		rules.putIfAbsent("material_id", () -> c.id("material_id", MATERIALS));
		rules.putIfAbsent("attributes", () -> c.requiredList("attributes"));
		rules.putIfAbsent("attributes.*", () -> c.itemsAreArrays("attributes"));
		rules.putIfAbsent("attributes.*.attribute_key", () -> c.attributeKey(definition));
		rules.putIfAbsent("attributes.*." + definition, () -> c.eachItem("attributes", definition,
			(node, path) -> c.idAt(node, path, "production_product_attribute_definitions")));
		rules.putIfAbsent("attributes.*.value_boolean", () -> c.eachItem("attributes", "value_boolean",
			(node, path) -> c.booleanAt(node, path)));
		rules.putIfAbsent("attributes.*.value_integer", () -> c.eachItem("attributes", "value_integer",
			(node, path) -> c.integerAt(node, path)));
		rules.putIfAbsent("attributes.*.value_decimal", () -> c.eachItem("attributes", "value_decimal",
			(node, path) -> c.numberAt(node, path)));
		rules.putIfAbsent("attributes.*.value_text", () -> c.eachItem("attributes", "value_text",
			(node, path) -> c.stringAt(node, path)));
		rules.putIfAbsent("attributes.*.value_json", () -> c.eachItem("attributes", "value_json",
			(node, path) -> c.arrayAt(node, path)));
	}

	/** Hareket turu: gonderilen, yoksa asamada sabitlenen, o da yoksa inbound. Transferde tanklar from/to. */
	private static void storageMovement(Map<String, Runnable> rules, Check c, List<Extension> extensions) {
		String action = c.field("action") != null ? c.field("action").asString() : StageExtensions.pinnedAction(extensions);
		boolean transfer = "transfer".equals(action == null ? "inbound" : action);
		rules.putIfAbsent("action", () -> c.action());
		rules.putIfAbsent("material_id", () -> c.id("material_id", MATERIALS));
		rules.putIfAbsent("quantity", () -> c.positive("quantity"));
		rules.putIfAbsent("note", () -> c.stringAt(c.field("note"), ROOT + ".note"));
		rules.putIfAbsent("storage_unit_id", () -> c.unitId("storage_unit_id", !transfer));
		rules.putIfAbsent("from_storage_unit_id", () -> c.unitId("from_storage_unit_id", transfer));
		rules.putIfAbsent("to_storage_unit_id", () -> c.toUnit(transfer));
	}

	/** On dinlendirme tanki ve miktar zorunlu; materyal birden coksa secilir. */
	private static void preSettling(Map<String, Runnable> rules, Check c) {
		rules.putIfAbsent("material_id", () -> c.id("material_id", MATERIALS));
		rules.putIfAbsent("quantity", () -> c.positive("quantity"));
		rules.putIfAbsent("note", () -> c.stringAt(c.field("note"), ROOT + ".note"));
		rules.putIfAbsent("storage_unit_id", () -> c.unitId("storage_unit_id", true));
	}

	private interface NodeCheck {

		void run(JsonNode node, String path);

	}

	/** Tek bir istegin alan kurallari. */
	private final class Check {

		private final ValidationErrors errors;
		private final JsonNode data;

		Check(ValidationErrors errors, JsonNode data) {
			this.errors = errors;
			this.data = data;
		}

		JsonNode field(String name) {
			JsonNode value = data == null ? null : data.get(name);
			return OperationData.isPresent(value) ? value : null;
		}

		private String path(String name) {
			return ROOT + "." + name;
		}

		/** {@code required|array}: bos dizi yok sayilir. */
		void root() {
			if (data == null || data.isNull() || (data.isContainer() && data.isEmpty())) {
				errors.add(ROOT, MessageKeys.REQUIRED);
			}
			else if (!data.isContainer()) {
				errors.add(ROOT, MessageKeys.ARRAY);
			}
		}

		void positive(String name) {
			positiveAt(field(name), path(name));
		}

		void positiveAt(JsonNode node, String path) {
			if (!OperationData.isPresent(node)) {
				errors.add(path, MessageKeys.REQUIRED);
				return;
			}
			BigDecimal value = OperationData.decimal(node);
			if (value == null) {
				errors.add(path, MessageKeys.NUMERIC);
				errors.add(path, MessageKeys.GT, "value", 0);
			}
			else if (value.signum() <= 0) {
				errors.add(path, MessageKeys.GT, "value", 0);
			}
		}

		void unit(String name) {
			unitAt(field(name), path(name));
		}

		void unitAt(JsonNode node, String path) {
			if (!OperationData.isPresent(node)) {
				return;
			}
			stringAt(node, path);
			if (!node.isString() || !List.of("KG", "LITRE").contains(node.asString())) {
				errors.add(path, MessageKeys.IN);
			}
		}

		void id(String name, String table) {
			idAt(field(name), path(name), table);
		}

		void idAt(JsonNode node, String path, String table) {
			if (!OperationData.isPresent(node)) {
				return;
			}
			Long id = OperationData.integer(node);
			if (id == null) {
				errors.add(path, MessageKeys.INTEGER);
			}
			else if (!queries.exists(table, id)) {
				errors.add(path, MessageKeys.EXISTS);
			}
		}

		void stringAt(JsonNode node, String path) {
			if (OperationData.isPresent(node) && !node.isString()) {
				errors.add(path, MessageKeys.STRING);
			}
		}

		void booleanAt(JsonNode node, String path) {
			if (OperationData.isPresent(node) && !node.isBoolean()) {
				errors.add(path, MessageKeys.BOOLEAN);
			}
		}

		void integerAt(JsonNode node, String path) {
			if (OperationData.isPresent(node) && OperationData.integer(node) == null) {
				errors.add(path, MessageKeys.INTEGER);
			}
		}

		void numberAt(JsonNode node, String path) {
			if (OperationData.isPresent(node) && OperationData.decimal(node) == null) {
				errors.add(path, MessageKeys.NUMERIC);
			}
		}

		void arrayAt(JsonNode node, String path) {
			if (OperationData.isPresent(node) && !node.isContainer()) {
				errors.add(path, MessageKeys.ARRAY);
			}
		}

		void requiredList(String name) {
			JsonNode node = field(name);
			if (node == null || (node.isContainer() && node.isEmpty())) {
				errors.add(path(name), MessageKeys.REQUIRED);
			}
			else if (!node.isContainer()) {
				errors.add(path(name), MessageKeys.ARRAY);
			}
		}

		private List<Map.Entry<String, JsonNode>> entries(String list) {
			JsonNode node = field(list);
			List<Map.Entry<String, JsonNode>> out = new ArrayList<>();
			if (node == null || !node.isContainer()) {
				return out;
			}
			if (node.isObject()) {
				node.properties().forEach(e -> out.add(Map.entry(e.getKey(), e.getValue())));
				return out;
			}
			int index = 0;
			for (JsonNode item : node.values()) {
				out.add(Map.entry(String.valueOf(index++), item));
			}
			return out;
		}

		void itemsAreArrays(String list) {
			entries(list).forEach(e -> {
				if (!e.getValue().isContainer()) {
					errors.add(path(list) + "." + e.getKey(), MessageKeys.ARRAY);
				}
			});
		}

		void eachItem(String list, String name, NodeCheck check) {
			entries(list).forEach(e -> check.run(child(e.getValue(), name), path(list) + "." + e.getKey() + "." + name));
		}

		private JsonNode child(JsonNode item, String name) {
			JsonNode value = item.isObject() ? item.get(name) : null;
			return OperationData.isPresent(value) ? value : null;
		}

		/** Tur, urun ya da urun anahtarindan biri olmali. */
		void outputType() {
			String type = "production_product_type_id";
			for (Map.Entry<String, JsonNode> e : entries("outputs")) {
				String base = path("outputs") + "." + e.getKey() + ".";
				JsonNode value = child(e.getValue(), type);
				if (value == null && child(e.getValue(), "production_product_id") == null
					&& child(e.getValue(), "product_type_key") == null) {
					errors.add(base + type, MessageKeys.REQUIRED_WITHOUT_ALL, "others",
						base + "production_product_id / " + base + "product_type_key");
				}
				else {
					idAt(value, base + type, PRODUCT_TYPES);
				}
			}
		}

		void attributeKey(String definition) {
			for (Map.Entry<String, JsonNode> e : entries("attributes")) {
				String base = path("attributes") + "." + e.getKey() + ".";
				JsonNode key = child(e.getValue(), "attribute_key");
				if (key == null && child(e.getValue(), definition) == null) {
					errors.add(base + "attribute_key", MessageKeys.REQUIRED_WITHOUT, "other", base + definition);
				}
				else {
					stringAt(key, base + "attribute_key");
				}
			}
		}

		void action() {
			JsonNode node = field("action");
			if (node == null) {
				return;
			}
			stringAt(node, path("action"));
			if (!node.isString() || !List.of("inbound", "outbound", "transfer").contains(node.asString())) {
				errors.add(path("action"), MessageKeys.IN);
			}
		}

		void unitId(String name, boolean required) {
			JsonNode node = field(name);
			if (node == null && required) {
				errors.add(path(name), MessageKeys.REQUIRED);
				return;
			}
			idAt(node, path(name), UNITS);
		}

		void toUnit(boolean required) {
			unitId("to_storage_unit_id", required);
			JsonNode to = field("to_storage_unit_id");
			JsonNode from = field("from_storage_unit_id");
			if (to != null && from != null && to.equals(from)) {
				errors.add(path("to_storage_unit_id"), MessageKeys.DIFFERENT, "other",
					"operation data.from storage unit id");
			}
		}

	}

}
