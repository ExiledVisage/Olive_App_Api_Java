package com.olivamore.api.process.stageextensiontype;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Laravel {@code StageExtensionTypeController}: yalnizca liste (tipler seed ile gelir). */
@RestController
class StageExtensionTypeController {

	private final Db db;
	private final StageExtensionTypeResource resource;

	StageExtensionTypeController(Db db, StageExtensionTypeResource resource) {
		this.db = db;
		this.resource = resource;
	}

	@GetMapping("/api/stage-extension-types")
	@RequiresPermission("production_stage.manage")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(resource.types(db.select("select * from stage_extension_types order by name asc")));
	}

}
