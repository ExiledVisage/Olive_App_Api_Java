package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Son tank analizi ve olcum gecmisi (sapma: hesaplanan ile olculen arasindaki fark). Yeni olcum 201. */
@RestController
class TankMeasurementController {

	private final TankMeasurementService measurements;

	TankMeasurementController(TankMeasurementService measurements) {
		this.measurements = measurements;
	}

	@PostMapping("/api/storage-units/{id}/measurements")
	@PreAuthorize("hasAuthority('storage_unit.measure')")
	ResponseEntity<DataResponse<List<TankMeasurementResponse>>> measure(@PathVariable long id,
		@Valid @RequestBody TankMeasurementRequest request, @AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(measurements.measure(id, request, user.id()));
	}

	/** En yeni once; {@code ?material_id} verilirse yalnizca o tank materyali. */
	@GetMapping("/api/storage-units/{id}/measurements")
	@PreAuthorize("hasAuthority('storage_movement.view')")
	DataResponse<List<TankMeasurementResponse>> list(@PathVariable long id,
		@RequestParam(name = "material_id", required = false) Long materialId) {
		return DataResponse.of(measurements.list(id, materialId));
	}

}
