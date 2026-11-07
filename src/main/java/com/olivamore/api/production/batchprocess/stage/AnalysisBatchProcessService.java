package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.olivamore.api.production.batchprocess.stage.StageExtensions.Extension;
import org.springframework.stereotype.Service;

/** Laravel {@code AnalysisBatchProcessService}: materyalin attribute degerleri (updateOrCreate). */
@Service
class AnalysisBatchProcessService extends StageBatchProcessService {

	AnalysisBatchProcessService(Db db, StockOperations stock) {
		super(db, stock);
	}

	void analysis(Row process, List<Extension> extensions, Map<String, Object> data) {
		Extension extension = require(extensions, "analysis", "Analysis extension bulunamadı.");
		Row material;
		if (truthy(data.get("material_id"))) {
			material = findOrFail("materials", "Material", data.get("material_id"));
			if (Php.intval(material.get("production_batch_id")) != Php.intval(process.get("production_batch_id"))) {
				throw new BusinessRuleException("Seçilen material bu batch’e ait değil.");
			}
		}
		else {
			List<Row> materials = candidateMaterials(process, extension.inputProductIds());
			if (materials.isEmpty()) {
				throw new BusinessRuleException("Analysis için uygun material bulunamadı.");
			}
			if (materials.size() > 1) {
				throw new BusinessRuleException("Birden fazla uygun material bulundu. material_id gönderilmelidir.");
			}
			material = materials.getFirst();
		}
		List<Map<String, Object>> attributes = items(data.get("attributes"));
		if (attributes.isEmpty()) {
			throw new BusinessRuleException("Analysis işlemi için attributes zorunludur.");
		}
		Object productId = db.find("production_product_types", Php.intval(material.get("production_product_type_id"))).orElseThrow()
			.get("production_product_id");
		for (Map<String, Object> attribute : attributes) {
			Row definition = definition(productId, attribute);
			Map<String, Object> payload = attributePayload(definition, attribute);
			Row existing = db.first("select * from material_attribute_values where material_attribute_values.material_id = ?"
				+ " and material_attribute_values.material_id is not null and production_product_attribute_definition_id = ? limit 1",
				material.id(), definition.id()).orElse(null);
			if (existing == null) {
				Map<String, Object> values = new LinkedHashMap<>();
				values.put("production_product_attribute_definition_id", definition.id());
				values.putAll(payload);
				values.put("material_id", material.id());
				db.insert("material_attribute_values", values);
			}
			else {
				db.update("material_attribute_values", existing.id(), payload);
			}
		}
	}

	private Row definition(Object productId, Map<String, Object> attribute) {
		if (truthy(attribute.get("production_product_attribute_definition_id"))) {
			Object id = attribute.get("production_product_attribute_definition_id");
			return db.first("select * from production_product_attribute_definitions where production_product_id = ?"
				+ " and production_product_attribute_definitions.id = ? limit 1", productId, id)
				.orElseThrow(() -> new ModelNotFoundException("ProductionProductAttributeDefinition", id));
		}
		if (truthy(attribute.get("attribute_key"))) {
			Row found = db.first("select * from production_product_attribute_definitions where production_product_id = ? and exists"
				+ " (select * from attribute_definitions where production_product_attribute_definitions.attribute_definition_id"
				+ " = attribute_definitions.id and attribute_key = ?) limit 1", productId, attribute.get("attribute_key")).orElse(null);
			if (found != null) {
				return found;
			}
		}
		throw new BusinessRuleException("Attribute definition çözümlenemedi.");
	}

	/** Tek {@code value} verilip value_* verilmediyse tanimin veri tipine gore; degilse value_* oldugu gibi. */
	private Map<String, Object> attributePayload(Row definition, Map<String, Object> attribute) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("value_boolean", null);
		payload.put("value_integer", null);
		payload.put("value_decimal", null);
		payload.put("value_text", null);
		payload.put("value_json", Db.json(null));
		boolean typed = List.of("value_boolean", "value_integer", "value_decimal", "value_text", "value_json").stream()
			.anyMatch(attribute::containsKey);
		if (!typed && attribute.containsKey("value")) {
			Object value = attribute.get("value");
			Row attributeDefinition = db.find("attribute_definitions", Php.intval(definition.get("attribute_definition_id"))).orElse(null);
			String dataType = attributeDefinition == null || attributeDefinition.get("data_type") == null ? null
				: Php.strval(attributeDefinition.get("data_type"));
			switch (dataType == null ? "" : dataType) {
				case "boolean" -> payload.put("value_boolean", truthy(value));
				case "integer" -> payload.put("value_integer", Php.intval(value));
				case "decimal" -> payload.put("value_decimal", decimal4(Php.floatval(value)));
				case "string", "text" -> payload.put("value_text", Php.strval(value));
				case "json" -> payload.put("value_json", Db.json(StageExtensions.phpJson(value)));
				default -> throw new BusinessRuleException("Desteklenmeyen attribute data_type değeri: '" + (dataType == null ? "" : dataType) + "'.");
			}
			return payload;
		}
		payload.put("value_boolean", attribute.get("value_boolean"));
		payload.put("value_integer", attribute.get("value_integer"));
		payload.put("value_decimal", attribute.get("value_decimal"));
		payload.put("value_text", attribute.get("value_text"));
		payload.put("value_json", Db.json(StageExtensions.phpJson(attribute.get("value_json"))));
		return payload;
	}

}
