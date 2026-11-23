package com.olivamore.api.machine;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** Makineler ve parcalari. Her makinede en az bir parca kalir. */
@Service
class MachineService {

	private final MachineRepository machines;
	private final MachinePartRepository parts;
	private final MachineTypeRepository types;

	MachineService(MachineRepository machines, MachinePartRepository parts, MachineTypeRepository types) {
		this.machines = machines;
		this.parts = parts;
		this.types = types;
	}

	@Transactional(readOnly = true)
	List<MachineResponse> all(Long machineTypeId, Boolean isActive) {
		List<Specification<Machine>> filters = new ArrayList<>();
		if (machineTypeId != null) {
			filters.add((root, query, cb) -> cb.equal(root.get("machineType").get("id"), machineTypeId));
		}
		if (isActive != null) {
			filters.add((root, query, cb) -> cb.equal(root.get("active"), isActive));
		}
		return machines.findAll(Specification.allOf(filters), Sort.by("machineType.sortOrder", "code", "id")).stream()
			.map(MachineResponse::of).toList();
	}

	@Transactional(readOnly = true)
	MachineResponse get(long id) {
		return MachineResponse.of(find(id));
	}

	@Transactional
	MachineResponse create(CreateMachineRequest request) {
		ValidationErrors errors = new ValidationErrors();
		MachineType type = types.findById(request.machineTypeId()).orElse(null);
		if (type == null) {
			errors.add("machine_type_id", MessageKeys.EXISTS);
		}
		if (machines.existsByCode(request.code())) {
			errors.add("code", MessageKeys.UNIQUE);
		}
		if (request.parts() != null) {
			errors.distinct("parts", "code", request.parts().stream().map(CreateMachinePartRequest::code).toList());
		}
		ObjectNode attributes = type == null ? null : MachineAttributes.check(type, request.attributes(), errors);
		errors.throwIfAny();
		if (!type.isActive()) {
			throw new BusinessRuleException("'" + type.getName() + "' makine tipi pasif; bu tipte makine eklenemez.");
		}
		return MachineResponse.of(machines.save(new Machine(type, request, attributes)));
	}

	@Transactional
	MachineResponse update(long id, UpdateMachineRequest request) {
		Machine machine = find(id);
		if (request.code() != null && machines.existsByCodeAndIdNot(request.code(), id)) {
			unique("code");
		}
		ObjectNode attributes = null;
		if (request.attributes() != null) {
			ValidationErrors errors = new ValidationErrors();
			attributes = MachineAttributes.check(machine.getMachineType(), request.attributes(), errors);
			errors.throwIfAny();
		}
		machine.apply(request, attributes);
		machines.flush();
		return MachineResponse.of(machine);
	}

	@Transactional
	void delete(long id) {
		machines.delete(find(id));
	}

	@Transactional
	MachineResponse.Part createPart(long machineId, CreateMachinePartRequest request) {
		Machine machine = find(machineId);
		if (parts.existsByMachineIdAndCode(machineId, request.code())) {
			unique("code");
		}
		return MachineResponse.Part.of(parts.save(new MachinePart(machine, request)));
	}

	@Transactional
	MachineResponse.Part updatePart(long machineId, long partId, UpdateMachinePartRequest request) {
		MachinePart part = findPart(machineId, partId);
		if (request.code() != null && parts.existsByMachineIdAndCodeAndIdNot(machineId, request.code(), partId)) {
			unique("code");
		}
		part.apply(request);
		return MachineResponse.Part.of(part);
	}

	@Transactional
	void deletePart(long machineId, long partId) {
		MachinePart part = findPart(machineId, partId);
		if (parts.countByMachineId(machineId) <= 1) {
			throw new BusinessRuleException("'" + part.getMachine().getName() + "' makinesinin tek parçası silinemez; "
				+ "her makinede en az bir parça olmalı.");
		}
		// Makinenin parca listesinde kalirsa (PERSIST) silme geri alinir
		part.getMachine().getParts().remove(part);
		parts.delete(part);
	}

	private Machine find(long id) {
		return machines.findById(id).orElseThrow(() -> new ModelNotFoundException("Machine", id));
	}

	private MachinePart findPart(long machineId, long partId) {
		find(machineId);
		MachinePart part = parts.findById(partId).orElseThrow(() -> new ModelNotFoundException("MachinePart", partId));
		if (part.getMachine().getId() != machineId) {
			throw new HttpStatusException(404, "Bu parça bu makineye ait değil.");
		}
		return part;
	}

	private static void unique(String field) {
		ValidationErrors errors = new ValidationErrors();
		errors.add(field, MessageKeys.UNIQUE);
		errors.throwIfAny();
	}

}
