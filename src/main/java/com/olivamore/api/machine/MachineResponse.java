package com.olivamore.api.machine;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

record MachineResponse(
	Long id,
	Type machineType,
	String code,
	String name,
	String brand,
	String model,
	BigDecimal capacityKgPerHour,
	String note,
	JsonNode attributes,
	boolean isActive,
	List<Part> parts,
	MachineActivity.Activity activity,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	record Type(Long id, String key, String name) {
	}

	record Part(Long id, Long machineId, String code, String name, int sortOrder, boolean isActive) {

		static Part of(MachinePart part) {
			return new Part(part.getId(), part.getMachine().getId(), part.getCode(), part.getName(),
				part.getSortOrder(), part.isActive());
		}

	}

	static MachineResponse of(Machine machine, MachineActivity.Activity activity) {
		MachineType type = machine.getMachineType();
		return new MachineResponse(machine.getId(), new Type(type.getId(), type.getKey(), type.getName()),
			machine.getCode(), machine.getName(), machine.getBrand(), machine.getModel(),
			machine.getCapacityKgPerHour(), machine.getNote(), machine.getAttributes(), machine.isActive(),
			machine.getParts().stream().map(Part::of).toList(), activity, machine.getCreatedAt(), machine.getUpdatedAt());
	}

}
