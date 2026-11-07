package com.olivamore.api.inventory.material;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Laravel {@code MaterialController}: materyal listesi, detay, soy grafi. */
@RestController
class MaterialController {

	private final MaterialService materials;
	private final MaterialDetailResource detail;
	private final MaterialLineageService lineage;

	MaterialController(MaterialService materials, MaterialDetailResource detail, MaterialLineageService lineage) {
		this.materials = materials;
		this.detail = detail;
		this.lineage = lineage;
	}

	/** {@code ?status}, {@code ?lineage}, {@code ?search}; en yeni 200. */
	@GetMapping("/api/materials")
	@RequiresPermission("storage_unit.view")
	Map<String, Object> index(HttpServletRequest request) {
		return materials.index(request.getParameter("status"), request.getParameter("lineage"), request.getParameter("search"));
	}

	@GetMapping("/api/materials/{material}/lineage")
	@RequiresPermission("storage_unit.view")
	DataResponse<Map<String, Object>> lineage(@Bound(table = "materials", model = "Material") Row material) {
		return DataResponse.of(lineage.build(material));
	}

	/** MaterialDetailResource */
	@GetMapping("/api/materials/{material}")
	@RequiresPermission("storage_unit.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "material", table = "materials", model = "Material") Row m) {
		return DataResponse.of(detail.of(m));
	}

}
