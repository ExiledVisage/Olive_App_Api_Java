package com.olivamore.api.machine;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Makine tipi alanlarina girilen degerler ({@code field_key -> deger}): anahtar tipin o kapsamdaki alani olmali, deger
 * veri tipine uymali; aktif zorunlu alan bos birakilamaz. Bos (null) deger kaydedilmez. Pasif alanin eski degeri
 * gonderilirse denetlenip korunur.
 * <p>
 * Makine bilgisinde (scope machine) min/max disi deger hatadir; calisma olcumunde (scope run) kaydedilir ve
 * "aralik disi" olarak isaretlenir (docs/MACHINES.md, acik konular).
 */
final class MachineFieldValues {

	private static final String UNKNOWN_ATTRIBUTE = "Bu makine tipinde böyle bir makine bilgisi alanı yok.";
	private static final String UNKNOWN_MEASUREMENT = "Bu makine tipinde böyle bir ölçüm alanı yok.";

	private MachineFieldValues() {
	}

	/** Makine bilgisi: tipin {@code machine} alanlari, aralik disi hata. */
	static ObjectNode attributes(MachineType type, JsonNode given, ValidationErrors errors) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		check(type, MachineTypeField.MACHINE, given, "attributes", UNKNOWN_ATTRIBUTE, true, errors)
			.forEach((field, value) -> result.set(field.getFieldKey(), value));
		return result;
	}

	/** Calisma olcumleri: tipin {@code run} alanlari; aralik disi kaydedilir. Sira tipin alan sirasi. */
	static Map<MachineTypeField, JsonNode> runValues(MachineType type, JsonNode given, ValidationErrors errors) {
		return check(type, MachineTypeField.RUN, given, "values", UNKNOWN_MEASUREMENT, false, errors);
	}

	static boolean outOfRange(MachineTypeField field, JsonNode value) {
		if (!field.numeric() || value == null || !value.isNumber()) {
			return false;
		}
		BigDecimal number = value.decimalValue();
		return field.getMinValue() != null && number.compareTo(field.getMinValue()) < 0
			|| field.getMaxValue() != null && number.compareTo(field.getMaxValue()) > 0;
	}

	private static Map<MachineTypeField, JsonNode> check(MachineType type, String scope, JsonNode given, String path,
		String unknown, boolean strictRange, ValidationErrors errors) {
		Map<MachineTypeField, JsonNode> result = new LinkedHashMap<>();
		if (given != null && !given.isNull() && !given.isObject()) {
			errors.add(path, MessageKeys.ARRAY);
			return result;
		}
		List<MachineTypeField> scoped = type.getFields().stream().filter(f -> scope.equals(f.getScope())).toList();
		Map<String, MachineTypeField> byKey = scoped.stream()
			.collect(Collectors.toMap(MachineTypeField::getFieldKey, Function.identity()));
		Map<String, JsonNode> accepted = new LinkedHashMap<>();
		if (given != null && given.isObject()) {
			for (Map.Entry<String, JsonNode> entry : given.properties()) {
				String at = path + "." + entry.getKey();
				MachineTypeField field = byKey.get(entry.getKey());
				JsonNode value = entry.getValue();
				if (field == null) {
					errors.add(at, unknown);
				}
				else if (!value.isNull() && valid(field, value, at, strictRange, errors)) {
					accepted.put(entry.getKey(), value);
				}
			}
		}
		for (MachineTypeField field : scoped) {
			if (accepted.containsKey(field.getFieldKey())) {
				result.put(field, accepted.get(field.getFieldKey()));
			}
			else if (field.isActive() && field.isRequired()) {
				errors.add(path + "." + field.getFieldKey(), MessageKeys.REQUIRED);
			}
		}
		return result;
	}

	private static boolean valid(MachineTypeField field, JsonNode value, String path, boolean strictRange,
		ValidationErrors errors) {
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
		if (!strictRange || !outOfRange(field, value)) {
			return true;
		}
		BigDecimal number = value.decimalValue();
		if (field.getMinValue() != null && number.compareTo(field.getMinValue()) < 0) {
			errors.add(path, MessageKeys.MIN, "value", field.getMinValue().stripTrailingZeros().toPlainString());
		}
		else {
			errors.add(path, MessageKeys.MAX, "value", field.getMaxValue().stripTrailingZeros().toPlainString());
		}
		return false;
	}

}
