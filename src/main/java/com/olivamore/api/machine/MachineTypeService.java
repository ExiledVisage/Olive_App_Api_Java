package com.olivamore.api.machine;

import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Makine tipleri ve olcum alanlari. Makinesi olan tip silinmez, pasife alinir. */
@Service
class MachineTypeService {

	private static final String USE_PASSIVE = "Pasife alabilirsiniz.";
	private static final String ONLY_NUMERIC = "En küçük ve en büyük değer yalnızca sayısal alanda verilir.";

	private final MachineTypeRepository types;
	private final MachineTypeFieldRepository fields;
	private final MachineRunValueRepository values;

	MachineTypeService(MachineTypeRepository types, MachineTypeFieldRepository fields,
		MachineRunValueRepository values) {
		this.types = types;
		this.fields = fields;
		this.values = values;
	}

	@Transactional(readOnly = true)
	List<MachineTypeResponse> all() {
		Map<Long, Long> counts = types.countMachines().stream().collect(Collectors.toMap(
			MachineTypeRepository.MachineCount::getMachineTypeId, MachineTypeRepository.MachineCount::getTotal));
		return types.findAll(Sort.by("sortOrder", "id")).stream()
			.map(type -> MachineTypeResponse.of(type, counts.getOrDefault(type.getId(), 0L)))
			.toList();
	}

	@Transactional(readOnly = true)
	MachineTypeResponse get(long id) {
		return detail(find(id));
	}

	@Transactional
	MachineTypeResponse create(CreateMachineTypeRequest request) {
		ValidationErrors errors = new ValidationErrors();
		if (types.existsByKey(request.key())) {
			errors.add("key", MessageKeys.UNIQUE);
		}
		if (request.fields() != null) {
			errors.distinct("fields", "field_key",
				request.fields().stream().map(CreateMachineTypeFieldRequest::fieldKey).toList());
			for (int i = 0; i < request.fields().size(); i++) {
				CreateMachineTypeFieldRequest field = request.fields().get(i);
				checkRange(errors, "fields." + i + ".", field.dataType(), field.minValue(), field.maxValue());
			}
		}
		errors.throwIfAny();
		return detail(types.save(new MachineType(request)));
	}

	@Transactional
	MachineTypeResponse update(long id, UpdateMachineTypeRequest request) {
		MachineType type = find(id);
		type.apply(request);
		types.flush();
		return detail(type);
	}

	@Transactional
	void delete(long id) {
		MachineType type = find(id);
		DeletionGuard.of("'" + type.getName() + "' makine tipi")
			.usedBy("makine", types.countMachines(id))
			.check(USE_PASSIVE);
		types.delete(type);
	}

	@Transactional
	MachineTypeResponse.Field createField(long typeId, CreateMachineTypeFieldRequest request) {
		MachineType type = find(typeId);
		ValidationErrors errors = new ValidationErrors();
		if (fields.existsByMachineTypeIdAndFieldKey(typeId, request.fieldKey())) {
			errors.add("field_key", MessageKeys.UNIQUE);
		}
		checkRange(errors, "", request.dataType(), request.minValue(), request.maxValue());
		errors.throwIfAny();
		return MachineTypeResponse.Field.of(fields.save(new MachineTypeField(type, request)));
	}

	@Transactional
	MachineTypeResponse.Field updateField(long typeId, long fieldId, UpdateMachineTypeFieldRequest request) {
		MachineTypeField field = findField(typeId, fieldId);
		field.apply(request);
		ValidationErrors errors = new ValidationErrors();
		checkRange(errors, "", field.getDataType(), field.getMinValue(), field.getMaxValue());
		errors.throwIfAny();
		return MachineTypeResponse.Field.of(field);
	}

	@Transactional
	void deleteField(long typeId, long fieldId) {
		MachineTypeField field = findField(typeId, fieldId);
		DeletionGuard.of("'" + field.getLabel() + "' alanı")
			.usedBy("makine adımı ölçümü", values.countByFieldId(fieldId))
			.check(USE_PASSIVE);
		// Tipin alan listesinde kalirsa (PERSIST) silme geri alinir
		field.getMachineType().getFields().remove(field);
		fields.delete(field);
	}

	/** Min/max yalnizca sayisal alanda ve min <= max. */
	private static void checkRange(ValidationErrors errors, String prefix, String dataType, BigDecimal min,
		BigDecimal max) {
		boolean numeric = "decimal".equals(dataType) || "integer".equals(dataType);
		if (!numeric && min != null) {
			errors.add(prefix + "min_value", ONLY_NUMERIC);
		}
		if (!numeric && max != null) {
			errors.add(prefix + "max_value", ONLY_NUMERIC);
		}
		if (numeric && min != null && max != null && min.compareTo(max) > 0) {
			errors.add(prefix + "max_value", MessageKeys.MIN, "value", min.toPlainString());
		}
	}

	private MachineTypeResponse detail(MachineType type) {
		return MachineTypeResponse.of(type, types.countMachines(type.getId()));
	}

	private MachineType find(long id) {
		return types.findById(id).orElseThrow(() -> new ModelNotFoundException("MachineType", id));
	}

	private MachineTypeField findField(long typeId, long fieldId) {
		find(typeId);
		MachineTypeField field = fields.findById(fieldId)
			.orElseThrow(() -> new ModelNotFoundException("MachineTypeField", fieldId));
		if (field.getMachineType().getId() != typeId) {
			throw new HttpStatusException(404, "Bu alan bu makine tipine ait değil.");
		}
		return field;
	}

}
