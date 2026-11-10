package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.process.StageExtensionTypeFieldResponse;
import com.olivamore.api.productionline.ProductCatalog;
import com.olivamore.api.productionline.stage.ProductionStageService;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Asamanin uzantilari silinip istekteki haliyle yeniden olusturulur. */
@Service
class StageExtensionService {

	private static final String INVALID_FIELD = "Geçersiz stage extension field gönderildi.";

	private final ExtensionRepository extensions;
	private final ExtensionValueRepository values;
	private final ExtensionInputProductRepository inputs;
	private final ExtensionOutputProductRepository outputs;
	private final ProductionStageService stages;
	private final ProcessResources process;
	private final ProductCatalog catalog;

	StageExtensionService(ExtensionRepository extensions, ExtensionValueRepository values,
		ExtensionInputProductRepository inputs, ExtensionOutputProductRepository outputs,
		ProductionStageService stages, ProcessResources process, ProductCatalog catalog) {
		this.extensions = extensions;
		this.values = values;
		this.inputs = inputs;
		this.outputs = outputs;
		this.stages = stages;
		this.process = process;
		this.catalog = catalog;
	}

	@Transactional
	void sync(long lineId, long stageId, SyncStageExtensionsRequest request) {
		stages.ensureExists(lineId, stageId);
		assertReferences(request.extensions());
		extensions.deleteForStage(stageId);
		for (int i = 0; i < request.extensions().size(); i++) {
			store(stageId, request.extensions().get(i), i + 1);
		}
	}

	private void store(long stageId, StageExtensionRequest request, int defaultOrder) {
		long extensionId = extensions.save(new ProductionStageExtension(stageId, request, defaultOrder)).getId();
		if (request.values() != null) {
			storeValues(extensionId, request.stageExtensionTypeId(), request.values());
		}
		List<Long> inputIds = orEmpty(request.inputProductionProductIds());
		for (int i = 0; i < inputIds.size(); i++) {
			inputs.save(new ExtensionInputProduct(extensionId, inputIds.get(i), i + 1));
		}
		List<Long> outputIds = orEmpty(request.outputProductionProductIds());
		for (int i = 0; i < outputIds.size(); i++) {
			outputs.save(new ExtensionOutputProduct(extensionId, outputIds.get(i), i + 1));
		}
	}

	private void storeValues(long extensionId, long typeId, List<StageExtensionValueRequest> requests) {
		List<StageExtensionTypeFieldResponse> fields =
			process.extensionTypeFields(List.of(typeId)).getOrDefault(typeId, List.of());
		for (StageExtensionValueRequest request : requests) {
			StageExtensionTypeFieldResponse field = findField(fields, request);
			if (field == null) {
				throw LaravelValidationException.of("extensions.values", INVALID_FIELD);
			}
			JsonNode json = request.valueJson() == null || request.valueJson().isNull() ? null : request.valueJson();
			boolean empty = request.valueBoolean() == null && request.valueInteger() == null
				&& request.valueDecimal() == null && request.valueText() == null && json == null;
			if (!empty) {
				values.save(new ProductionStageExtensionValue(extensionId, field.id(), request, json));
			}
		}
	}

	private static StageExtensionTypeFieldResponse findField(List<StageExtensionTypeFieldResponse> fields,
		StageExtensionValueRequest request) {
		if (request.stageExtensionTypeFieldId() != null) {
			return fields.stream()
				.filter(field -> field.id().equals(request.stageExtensionTypeFieldId()))
				.findFirst().orElse(null);
		}
		if (request.fieldKey() != null && !request.fieldKey().isEmpty()) {
			return fields.stream()
				.filter(field -> field.fieldKey().equals(request.fieldKey()))
				.findFirst().orElse(null);
		}
		return null;
	}

	private void assertReferences(List<StageExtensionRequest> requests) {
		ValidationErrors errors = new ValidationErrors();
		List<Long> types = requests.stream().map(StageExtensionRequest::stageExtensionTypeId).toList();
		errors.exists("extensions", "stage_extension_type_id", types, process.extensionTypesById(types).keySet());
		errors.distinct("extensions", "stage_extension_type_id", types);
		for (int i = 0; i < requests.size(); i++) {
			StageExtensionRequest request = requests.get(i);
			checkValues(errors, "extensions." + i + ".values", orEmpty(request.values()));
			checkProducts(errors, "extensions." + i + ".input_production_product_ids",
				orEmpty(request.inputProductionProductIds()));
			checkProducts(errors, "extensions." + i + ".output_production_product_ids",
				orEmpty(request.outputProductionProductIds()));
		}
		errors.throwIfAny();
	}

	private void checkValues(ValidationErrors errors, String path, List<StageExtensionValueRequest> requests) {
		List<Long> fieldIds = requests.stream().map(StageExtensionValueRequest::stageExtensionTypeFieldId).toList();
		errors.exists(path, "stage_extension_type_field_id", fieldIds,
			process.extensionTypeFieldsById(fieldIds).keySet());
		for (int i = 0; i < requests.size(); i++) {
			JsonNode json = requests.get(i).valueJson();
			if (json != null && !json.isNull() && !json.isContainer()) {
				errors.add(path + "." + i + ".value_json", LaravelMessages.ARRAY);
			}
		}
	}

	private void checkProducts(ValidationErrors errors, String path, List<Long> productIds) {
		errors.exists(path, null, productIds, catalog.products(productIds).keySet());
		errors.distinct(path, null, productIds);
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

}
