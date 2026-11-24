package com.olivamore.api.machine;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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
	private final MachineActivity activity;

	MachineController(MachineService machines, MachineActivity activity) {
		this.machines = machines;
		this.activity = activity;
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

	/** Makinenin calismalari, en yeni once; {@code date_from}/{@code date_to} baslangic gunu, sayfali olabilir. */
	@GetMapping("/{id}/runs")
	@PreAuthorize("hasAuthority('machine.view')")
	PagedModel<MachineActivity.RunRow> runs(@PathVariable long id,
		@RequestParam(name = "date_from", required = false) String dateFrom,
		@RequestParam(name = "date_to", required = false) String dateTo, Pageable pageable) {
		return activity.runs(id, dateFrom, dateTo, pageable);
	}

	/** Sayisal olcum alaninin zaman serisi (grafik); donem {@code period} ya da tarih araligi. */
	@GetMapping("/{id}/series")
	@PreAuthorize("hasAuthority('machine.view')")
	DataResponse<MachineActivity.Series> series(@PathVariable long id, @RequestParam("field") String field,
		@RequestParam(name = "period", required = false) String period,
		@RequestParam(name = "date_from", required = false) String dateFrom,
		@RequestParam(name = "date_to", required = false) String dateTo) {
		return DataResponse.of(activity.series(id, field, period, dateFrom, dateTo));
	}

	/** Donem ozeti: calisma suresi, kullanim orani, partiler, kg, randiman, olcum istatistikleri. */
	@GetMapping("/{id}/summary")
	@PreAuthorize("hasAuthority('machine.view')")
	DataResponse<MachineActivity.Summary> summary(@PathVariable long id,
		@RequestParam(name = "period", required = false) String period,
		@RequestParam(name = "date_from", required = false) String dateFrom,
		@RequestParam(name = "date_to", required = false) String dateTo) {
		return DataResponse.of(activity.summary(id, period, dateFrom, dateTo));
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
