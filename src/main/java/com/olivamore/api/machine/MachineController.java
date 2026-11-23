package com.olivamore.api.machine;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Makineler ve parcalari. {@code ?machine_type_id}, {@code ?is_active} suzgec. */
@RestController
@RequestMapping("/api/machines")
class MachineController {

	private final MachineService machines;

	MachineController(MachineService machines) {
		this.machines = machines;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('machine.view')")
	ListResponse<MachineResponse> index(@RequestParam(name = "machine_type_id", required = false) Long machineTypeId,
		@RequestParam(name = "is_active", required = false) Boolean isActive) {
		return new ListResponse<>(machines.all(machineTypeId, isActive));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('machine.view')")
	DataResponse<MachineResponse> show(@PathVariable long id) {
		return DataResponse.of(machines.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('machine.manage')")
	ResponseEntity<DataResponse<MachineResponse>> store(@Valid @RequestBody CreateMachineRequest request) {
		return DataResponse.created(machines.create(request));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('machine.manage')")
	DataResponse<MachineResponse> update(@PathVariable long id, @Valid @RequestBody UpdateMachineRequest request) {
		return DataResponse.of(machines.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('machine.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		machines.delete(id);
		return Map.of("message", "Makine silindi.");
	}

	@PostMapping("/{id}/parts")
	@PreAuthorize("hasAuthority('machine.manage')")
	ResponseEntity<DataResponse<MachineResponse.Part>> storePart(@PathVariable long id,
		@Valid @RequestBody CreateMachinePartRequest request) {
		return DataResponse.created(machines.createPart(id, request));
	}

	@PatchMapping("/{id}/parts/{partId}")
	@PreAuthorize("hasAuthority('machine.manage')")
	DataResponse<MachineResponse.Part> updatePart(@PathVariable long id, @PathVariable long partId,
		@Valid @RequestBody UpdateMachinePartRequest request) {
		return DataResponse.of(machines.updatePart(id, partId, request));
	}

	@DeleteMapping("/{id}/parts/{partId}")
	@PreAuthorize("hasAuthority('machine.manage')")
	Map<String, String> destroyPart(@PathVariable long id, @PathVariable long partId) {
		machines.deletePart(id, partId);
		return Map.of("message", "Parça silindi.");
	}

}
