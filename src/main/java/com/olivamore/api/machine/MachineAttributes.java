package com.olivamore.api.machine;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Makinenin bilgi alanlari (tipin {@code scope = machine} alanlari). Anahtar tipin makine bilgisi alani olmali, deger
 * veri tipine uymali, sayisal deger min/max icinde; aktif zorunlu alan bos birakilamaz. Bos (null) deger kaydedilmez.
 * Pasif alanin eski degeri gonderilirse denetlenip korunur.
 */
final class MachineAttributes {

	private static final String UNKNOWN = "Bu makine tipinde böyle bir makine bilgisi alanı yok.";

	private MachineAttributes() {
	}

	static ObjectNode check(MachineType type, JsonNode given, ValidationErrors errors) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		if (given != null && !given.isNull() && !given.isObject()) {
			errors.add("attributes", MessageKeys.ARRAY);
			return result;
		}
		Map<String, MachineTypeField> fields = type.getFields().stream().filter(MachineTypeField::forMachine)
			.collect(Collectors.toMap(MachineTypeField::getFieldKey, Function.identity()));
		if (given != null && given.isObject()) {
			for (Map.Entry<String, JsonNode> entry : given.properties()) {
				String path = "attributes." + entry.getKey();
				MachineTypeField field = fields.get(entry.getKey());
				JsonNode value = entry.getValue();
				if (field == null) {
					errors.add(path, UNKNOWN);
				}
				else if (!value.isNull() && valid(field, value, path, errors)) {
					result.set(entry.getKey(), value);
				}
			}
		}
		fields.values().stream()
			.filter(field -> field.isActive() && field.isRequired() && !result.has(field.getFieldKey()))
			.forEach(field -> errors.add("attributes." + field.getFieldKey(), MessageKeys.REQUIRED));
		return result;
	}

	private static boolean valid(MachineTypeField field, JsonNode value, String path, ValidationErrors errors) {
		String typeError = switch (field.getDataType()) {
			case "decimal" -> value.isNumber() ? null : MessageKeys.NUMERIC;
			case "integer" -> value.isIntegralNumber() ? null : MessageKeys.INTEGER;
			case "boolean" -> value.isBoolean() ? null : MessageKeys.BOOLEAN;
			default -> value.isString() ? null : MessageKeys.STRING;
		};
		if (typeError != null) {
			errors.add(path, typeError);
			return false;
		}
		if (!field.numeric()) {
			return true;
		}
		BigDecimal number = value.decimalValue();
		if (field.getMinValue() != null && number.compareTo(field.getMinValue()) < 0) {
			errors.add(path, MessageKeys.MIN, "value", field.getMinValue().stripTrailingZeros().toPlainString());
			return false;
		}
		if (field.getMaxValue() != null && number.compareTo(field.getMaxValue()) > 0) {
			errors.add(path, MessageKeys.MAX, "value", field.getMaxValue().stripTrailingZeros().toPlainString());
			return false;
		}
		return true;
	}

}
