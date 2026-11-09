package com.olivamore.api.process.stageextensiontype;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StageExtensionTypeController {

	private final StageExtensionTypeResponses responses;

	StageExtensionTypeController(StageExtensionTypeResponses responses) {
		this.responses = responses;
	}

	@GetMapping("/api/stage-extension-types")
	@RequiresPermission("production_stage.manage")
	ListResponse<StageExtensionTypeResponse> index() {
		return new ListResponse.Plain<>(responses.all());
	}

}
