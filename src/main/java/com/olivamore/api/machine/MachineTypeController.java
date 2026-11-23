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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Makine tipleri ve olcum alanlari. */
@RestController
@RequestMapping("/api/machine-types")
class MachineTypeController {

	private final MachineTypeService types;

	MachineTypeController(MachineTypeService types) {
		this.types = types;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('machine.view')")
	ListResponse<MachineTypeResponse> index() {
		return new ListResponse<>(types.all());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('machine.view')")
	DataResponse<MachineTypeResponse> show(@PathVariable long id) {
		return DataResponse.of(types.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('machine.manage')")
	ResponseEntity<DataResponse<MachineTypeResponse>> store(@Valid @RequestBody CreateMachineTypeRequest request) {
		return DataResponse.created(types.create(request));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('machine.manage')")
	DataResponse<MachineTypeResponse> update(@PathVariable long id,
		@Valid @RequestBody UpdateMachineTypeRequest request) {
		return DataResponse.of(types.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('machine.manage')")
	Map<String, String> destroy(@PathVariable long id) {
		types.delete(id);
		return Map.of("message", "Makine tipi silindi.");
	}

	@PostMapping("/{id}/fields")
	@PreAuthorize("hasAuthority('machine.manage')")
	ResponseEntity<DataResponse<MachineTypeResponse.Field>> storeField(@PathVariable long id,
		@Valid @RequestBody CreateMachineTypeFieldRequest request) {
		return DataResponse.created(types.createField(id, request));
	}

	@PatchMapping("/{id}/fields/{fieldId}")
	@PreAuthorize("hasAuthority('machine.manage')")
	DataResponse<MachineTypeResponse.Field> updateField(@PathVariable long id, @PathVariable long fieldId,
		@Valid @RequestBody UpdateMachineTypeFieldRequest request) {
		return DataResponse.of(types.updateField(id, fieldId, request));
	}

	@DeleteMapping("/{id}/fields/{fieldId}")
	@PreAuthorize("hasAuthority('machine.manage')")
	Map<String, String> destroyField(@PathVariable long id, @PathVariable long fieldId) {
		types.deleteField(id, fieldId);
		return Map.of("message", "Alan silindi.");
	}

}
