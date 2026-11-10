package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.inventory.StockOperations.AttributeValue;
import com.olivamore.api.production.batchprocess.stage.StageQueries.AttributeDefinition;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Materyalin attribute degerleri (tanim icin satir varsa degisir, yoksa eklenir). */
@Service
class AnalysisBatchProcessService extends StageBatchProcessService {

	private static final List<String> TYPED_VALUES = List.of("value_boolean", "value_integer", "value_decimal", "value_text",
		"value_json");

	AnalysisBatchProcessService(StageQueries queries, StockOperations stock) {
		super(queries, stock);
	}

	void analysis(StageProcess process, List<Extension> extensions, OperationData data) {
		Extension extension = require(extensions, "analysis", "Analysis extension bulunamadı.");
		Material material = material(process, extension, data);
		List<OperationData> attributes = data.items("attributes");
		if (attributes.isEmpty()) {
			throw new BusinessRuleException("Analysis işlemi için attributes zorunludur.");
		}
		long productId = queries.productType(material.productionProductTypeId()).orElseThrow().productionProductId();
		for (OperationData attribute : attributes) {
			AttributeDefinition definition = definition(productId, attribute);
			stock.recordAttribute(material.id(), definition.id(), value(definition, attribute));
		}
	}

	private Material material(StageProcess process, Extension extension, OperationData data) {
		Long selected = data.id("material_id");
		if (selected != null) {
			Material material = queries.material(selected)
				.orElseThrow(() -> new ModelNotFoundException("Material", selected));
			if (!Objects.equals(material.productionBatchId(), process.productionBatchId())) {
				throw new BusinessRuleException("Seçilen material bu batch’e ait değil.");
			}
			return material;
		}
		List<Material> materials = candidateMaterials(process, extension.inputProductIds());
		if (materials.isEmpty()) {
			throw new BusinessRuleException("Analysis için uygun material bulunamadı.");
		}
		if (materials.size() > 1) {
			throw new BusinessRuleException("Birden fazla uygun material bulundu. material_id gönderilmelidir.");
		}
		return materials.getFirst();
	}

	private AttributeDefinition definition(long productId, OperationData attribute) {
		Long id = attribute.id("production_product_attribute_definition_id");
		if (id != null) {
			return queries.definition(productId, id)
				.orElseThrow(() -> new ModelNotFoundException("ProductionProductAttributeDefinition", id));
		}
		if (attribute.isTruthy("attribute_key")) {
			var found = queries.definitionByKey(productId, attribute.text("attribute_key"));
			if (found.isPresent()) {
				return found.get();
			}
		}
		throw new BusinessRuleException("Attribute definition çözümlenemedi.");
	}

	/** Tek {@code value} verilip value_* verilmediyse tanimin veri tipine gore; degilse value_* oldugu gibi. */
	private AttributeValue value(AttributeDefinition definition, OperationData attribute) {
		boolean typed = TYPED_VALUES.stream().anyMatch(attribute::has);
		if (!typed && attribute.has("value")) {
			return singleValue(definition, attribute.value("value"));
		}
		JsonNode valueInteger = attribute.value("value_integer");
		JsonNode valueBoolean = attribute.value("value_boolean");
		return new AttributeValue(valueBoolean == null ? null : valueBoolean.booleanValue(),
			OperationData.integer(valueInteger), OperationData.decimal(attribute.value("value_decimal")),
			attribute.text("value_text"), attribute.value("value_json"));
	}

	private AttributeValue singleValue(AttributeDefinition definition, JsonNode value) {
		String dataType = queries.attributeDataType(definition.attributeDefinitionId()).orElse("");
		return switch (dataType) {
			case "boolean" -> new AttributeValue(booleanValue(value), null, null, null, null);
			case "integer" -> new AttributeValue(null, integerValue(value), null, null, null);
			case "decimal" -> new AttributeValue(null, null, numberOrZero(value), null, null);
			case "string", "text" -> new AttributeValue(null, null, null, textValue(value), null);
			case "json" -> new AttributeValue(null, null, null, null, value);
			default -> throw new BusinessRuleException("Desteklenmeyen attribute data_type değeri: '" + dataType + "'.");
		};
	}

	/** Yalnizca JSON true/false; "hayir" gibi bir metin sessizce "evet" kaydedilmez. */
	static boolean booleanValue(JsonNode value) {
		if (value == null || !value.isBoolean()) {
			throw new BusinessRuleException("Attribute değeri true ya da false olmalıdır.");
		}
		return value.booleanValue();
	}

	private static BigDecimal numberOrZero(JsonNode value) {
		BigDecimal number = OperationData.decimal(value);
		return number == null ? BigDecimal.ZERO : number;
	}

	/** Metinse sayisal degeri, ondalikliysa tam kismi; sayi degilse sifir. */
	private static long integerValue(JsonNode value) {
		return numberOrZero(value).longValue();
	}

	/** Dizi metne cevrilemez; sayi sondaki sifirlar olmadan yazilir. */
	static String textValue(JsonNode value) {
		if (value == null) {
			return "";
		}
		if (value.isContainer()) {
			throw new BusinessRuleException("Attribute metin değeri dizi olamaz.");
		}
		if (value.isNumber()) {
			return plain(value.decimalValue());
		}
		if (value.isBoolean()) {
			return value.booleanValue() ? "1" : "";
		}
		return value.asString();
	}

}
