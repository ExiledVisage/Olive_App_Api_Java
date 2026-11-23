package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.web.DataResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Sikim gibi makine hatli asamada makine adimlari; cevaplar guncel adim listesi. */
@RestController
class MachineRouteController {

	private final MachineRouteService routes;

	MachineRouteController(MachineRouteService routes) {
		this.routes = routes;
	}

	@GetMapping("/api/batch-processes/{id}/machine-runs")
	@PreAuthorize("hasAuthority('batch_process.view')")
	DataResponse<MachineRouteService.Route> show(@PathVariable long id) {
		return DataResponse.of(routes.route(id));
	}

	@PostMapping("/api/batch-processes/{id}/machine-runs/{step}/start")
	@PreAuthorize("hasAuthority('batch_process.fill')")
	DataResponse<MachineRouteService.Route> start(@PathVariable long id, @PathVariable int step,
		@RequestBody(required = false) MachineRunRequests.Start request) {
		routes.start(id, step, request == null ? null : request.machinePartId());
		return DataResponse.of(routes.route(id));
	}

	@PostMapping("/api/batch-processes/{id}/machine-runs/{step}/finish")
	@PreAuthorize("hasAuthority('batch_process.fill')")
	DataResponse<MachineRouteService.Route> finish(@PathVariable long id, @PathVariable int step,
		@RequestBody(required = false) MachineRunRequests.Finish request) {
		routes.finish(id, step, request == null ? null : request.values(), request == null ? null : request.note());
		return DataResponse.of(routes.route(id));
	}

	@PatchMapping("/api/batch-processes/{id}/machine-runs/{step}")
	@PreAuthorize("hasAuthority('batch_process.fill')")
	DataResponse<MachineRouteService.Route> correct(@PathVariable long id, @PathVariable int step,
		@RequestBody CorrectMachineRunRequest request) {
		routes.correct(id, step, request);
		return DataResponse.of(routes.route(id));
	}

}
