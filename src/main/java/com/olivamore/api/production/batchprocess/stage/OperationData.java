package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.shared.json.LaravelStringDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Asama tamamlanirken gonderilen serbest veri (seklini asamanin uzantilari belirler). Dogrulanmis veriyi
 * isleyicilere tipli verir; eksik alan {@code null}.
 */
public record OperationData(JsonNode root) {

	public OperationData {
		root = root == null ? MissingNode.getInstance() : root;
	}

	/** Metinler kirpilir, bos metin {@code null} olur (JSON govdesinin diger alanlariyla ayni kural). */
	public static JsonNode normalized(JsonNode node) {
		if (node == null) {
			return null;
		}
		JsonNodeFactory factory = JsonNodeFactory.instance;
		if (node.isString()) {
			String text = LaravelStringDeserializer.normalize(node.asString(), false);
			return text == null ? factory.nullNode() : factory.stringNode(text);
		}
		if (node.isArray()) {
			ArrayNode out = factory.arrayNode();
			node.values().forEach(item -> out.add(normalized(item)));
			return out;
		}
		if (node.isObject()) {
			ObjectNode out = factory.objectNode();
			node.properties().forEach(entry -> out.set(entry.getKey(), normalized(entry.getValue())));
			return out;
		}
		return node;
	}

	/** Bos ya da yok ise saklanmaz. */
	public static JsonNode forStorage(JsonNode node) {
		return isFilled(node) ? node : null;
	}

	/** Dolu dizi ya da nesne. */
	public static boolean isFilled(JsonNode node) {
		return node != null && node.isContainer() && !node.isEmpty();
	}

	public static boolean isPresent(JsonNode node) {
		return node != null && !node.isNull() && !node.isMissingNode();
	}

	/** Dogruluk degeri: sifir, bos metin, {@code "0"} ve bos dizi yanlis. */
	public static boolean truthy(JsonNode node) {
		if (!isPresent(node)) {
			return false;
		}
		if (node.isBoolean()) {
			return node.booleanValue();
		}
		if (node.isString()) {
			return !node.asString().isEmpty() && !node.asString().equals("0");
		}
		if (node.isNumber()) {
			return node.decimalValue().signum() != 0;
		}
		return !node.isContainer() || !node.isEmpty();
	}

	/** Sayisal metin ya da sayi; degilse {@code null}. */
	public static BigDecimal decimal(JsonNode node) {
		if (!isPresent(node)) {
			return null;
		}
		if (node.isNumber()) {
			return node.decimalValue();
		}
		if (node.isString()) {
			try {
				return new BigDecimal(node.asString());
			}
			catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/** Tam sayi: tam degerli sayi ya da tam sayi metni; degilse {@code null}. */
	public static Long integer(JsonNode node) {
		BigDecimal value = decimal(node);
		if (value == null || value.stripTrailingZeros().scale() > 0) {
			return null;
		}
		if (node.isString() && !node.asString().matches("[+-]?\\d+")) {
			return null;
		}
		try {
			return value.longValueExact();
		}
		catch (ArithmeticException e) {
			return null;
		}
	}

	public JsonNode value(String field) {
		JsonNode value = root.get(field);
		return isPresent(value) ? value : null;
	}

	public boolean has(String field) {
		return root.has(field);
	}

	public boolean isTruthy(String field) {
		return truthy(value(field));
	}

	/** Verilmisse (sifirdan farkli) id; yoksa {@code null}. */
	public Long id(String field) {
		return isTruthy(field) ? integer(value(field)) : null;
	}

	/** Verilmemisse sifir. */
	public BigDecimal quantity(String field) {
		BigDecimal value = decimal(value(field));
		return value == null ? BigDecimal.ZERO : value;
	}

	public String text(String field) {
		JsonNode value = value(field);
		return value == null ? null : value.asString();
	}

	/** Listedeki nesneler; nesne olmayan eleman bos veri gibi. */
	public List<OperationData> items(String field) {
		JsonNode value = value(field);
		List<OperationData> out = new ArrayList<>();
		if (value != null && value.isContainer()) {
			value.values().forEach(item -> out.add(new OperationData(item.isObject() ? item : null)));
		}
		return out;
	}

}
