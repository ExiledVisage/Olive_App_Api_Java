package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tank seviyesi: geometri (ayar, {@code storage_unit.manage}) ve cetvel okumalari ({@code storage_unit.measure});
 * okuma stoku degistirmez, defterle farki gosterir. Geometri girilmemisse GET {@code data: null}. Yeni okuma 201.
 */
@RestController
class TankLevelController {

	private final TankLevelService levels;

	TankLevelController(TankLevelService levels) {
		this.levels = levels;
	}

	@GetMapping("/api/storage-units/{id}/geometry")
	@PreAuthorize("hasAuthority('storage_unit.view')")
	DataResponse<TankLevelResponse.Geometry> geometry(@PathVariable long id) {
		return DataResponse.of(levels.geometry(id));
	}

	@PutMapping("/api/storage-units/{id}/geometry")
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	DataResponse<TankLevelResponse.Geometry> updateGeometry(@PathVariable long id,
		@Valid @RequestBody TankGeometryRequest request) {
		return DataResponse.of(levels.updateGeometry(id, request));
	}

	/** Cevap 204. */
	@DeleteMapping("/api/storage-units/{id}/geometry")
	@PreAuthorize("hasAuthority('storage_unit.manage')")
	ResponseEntity<Void> deleteGeometry(@PathVariable long id) {
		levels.deleteGeometry(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/storage-units/{id}/level-readings")
	@PreAuthorize("hasAuthority('storage_unit.measure')")
	ResponseEntity<DataResponse<TankLevelResponse.Reading>> read(@PathVariable long id,
		@Valid @RequestBody TankLevelReadingRequest request, @AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(levels.read(id, request, user.id()));
	}

	/** En yeni once. */
	@GetMapping("/api/storage-units/{id}/level-readings")
	@PreAuthorize("hasAuthority('storage_unit.view')")
	DataResponse<List<TankLevelResponse.Reading>> list(@PathVariable long id) {
		return DataResponse.of(levels.list(id));
	}

}
