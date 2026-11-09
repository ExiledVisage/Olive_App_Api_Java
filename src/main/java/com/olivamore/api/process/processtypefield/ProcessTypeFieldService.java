package com.olivamore.api.process.processtypefield;

import com.olivamore.api.process.processtype.ProcessTypeService;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
class ProcessTypeFieldService {

	private final ProcessTypeFieldRepository repository;
	private final ProcessTypeService processTypes;

	ProcessTypeFieldService(ProcessTypeFieldRepository repository, ProcessTypeService processTypes) {
		this.repository = repository;
		this.processTypes = processTypes;
	}

	@Transactional(readOnly = true)
	List<ProcessTypeField> forProcessType(long processTypeId) {
		processTypes.ensureExists(processTypeId);
		return repository.findByProcessTypeIdOrderBySortOrderAscIdAsc(processTypeId);
	}

	@Transactional(readOnly = true)
	ProcessTypeField get(long processTypeId, long fieldId) {
		return find(processTypeId, fieldId);
	}

	@Transactional
	ProcessTypeField create(long processTypeId, CreateProcessTypeFieldRequest request) {
		processTypes.ensureExists(processTypeId);
		if (repository.existsByProcessTypeIdAndFieldKey(processTypeId, request.fieldKey())) {
			throw fieldKeyTaken();
		}
		assertContainer(request.options());
		return repository.save(new ProcessTypeField(processTypeId, request));
	}

	@Transactional
	ProcessTypeField update(long processTypeId, long fieldId, UpdateProcessTypeFieldRequest request) {
		ProcessTypeField field = find(processTypeId, fieldId);
		if (request.fieldKey() != null
			&& repository.existsByProcessTypeIdAndFieldKeyAndIdNot(processTypeId, request.fieldKey(), fieldId)) {
			throw fieldKeyTaken();
		}
		Patch.ifPresent(request.options(), ProcessTypeFieldService::assertContainer);
		field.apply(request);
		return field;
	}

	@Transactional
	void delete(long processTypeId, long fieldId) {
		ProcessTypeField field = find(processTypeId, fieldId);
		DeletionGuard.of("'" + field.getLabel() + "' alanı")
			.usedBy("girilmiş ölçüm", repository.countMeasurements(fieldId))
			.check("Artık kullanılmayacaksa is_active alanını false yapın.");
		repository.delete(field);
	}

	private ProcessTypeField find(long processTypeId, long fieldId) {
		processTypes.ensureExists(processTypeId);
		ProcessTypeField field = repository.findById(fieldId)
			.orElseThrow(() -> new ModelNotFoundException("ProcessTypeField", fieldId));
		if (field.getProcessTypeId() != processTypeId) {
			throw new HttpStatusException(404, "Field not found for this process type.");
		}
		return field;
	}

	/** {@code options} JSON dizi ya da nesne olmali. */
	private static void assertContainer(JsonNode options) {
		if (options != null && !options.isNull() && !options.isContainer()) {
			throw LaravelValidationException.of("options", LaravelMessages.format(LaravelMessages.ARRAY, "options"));
		}
	}

	private static LaravelValidationException fieldKeyTaken() {
		return LaravelValidationException.of("field_key", LaravelMessages.format(LaravelMessages.UNIQUE, "field_key"));
	}

}
