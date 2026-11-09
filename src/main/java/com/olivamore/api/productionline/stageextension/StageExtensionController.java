package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.productionline.ProductionStageResponse;
import com.olivamore.api.productionline.stage.ProductionStageResponses;
import com.olivamore.api.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StageExtensionController {

	private final StageExtensionService service;
	private final ProductionStageResponses stages;

	StageExtensionController(StageExtensionService service, ProductionStageResponses stages) {
		this.service = service;
		this.stages = stages;
	}

	@PutMapping("/api/production-lines/{lineId}/stages/{stageId}/extensions")
	@RequiresPermission("production_stage.manage")
	DataResponse<ProductionStageResponse> sync(@PathVariable long lineId, @PathVariable long stageId,
		@Valid @RequestBody SyncStageExtensionsRequest request) {
		service.sync(lineId, stageId, request);
		return DataResponse.of(stages.one(stageId));
	}

}
