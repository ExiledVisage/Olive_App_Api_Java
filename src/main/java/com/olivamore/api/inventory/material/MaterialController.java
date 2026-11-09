package com.olivamore.api.inventory.material;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Materyal listesi, detay, soy grafi. */
@RestController
class MaterialController {

	private final MaterialService materials;
	private final MaterialLineageService lineage;

	MaterialController(MaterialService materials, MaterialLineageService lineage) {
		this.materials = materials;
		this.lineage = lineage;
	}

	/** {@code ?status}, {@code ?lineage}, {@code ?search}; en yeni 200. */
	@GetMapping("/api/materials")
	@RequiresPermission("storage_unit.view")
	MaterialListResponse index(@RequestParam(required = false) String status,
		@RequestParam(required = false) String lineage, @RequestParam(required = false) String search) {
		return materials.index(status, lineage, search);
	}

	@GetMapping("/api/materials/{id}/lineage")
	@RequiresPermission("storage_unit.view")
	DataResponse<MaterialLineageResponse> lineage(@PathVariable long id) {
		return DataResponse.of(lineage.build(id));
	}

	@GetMapping("/api/materials/{id}")
	@RequiresPermission("storage_unit.view")
	DataResponse<MaterialDetailResponse> show(@PathVariable long id) {
		return DataResponse.of(materials.detail(id));
	}

}
