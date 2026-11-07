package com.olivamore.api.productionline.stageextension;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.productionline.stage.ProductionStageResource;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Laravel {@code ProductionStageExtensionController} (production_stage.manage). */
@RestController
class ProductionStageExtensionController {

	private final Db db;
	private final LaravelValidator validator;
	private final ProductionStageExtensionService extensions;
	private final ProductionStageResource stageResource;

	ProductionStageExtensionController(Db db, LaravelValidator validator, ProductionStageExtensionService extensions,
		ProductionStageResource stageResource) {
		this.db = db;
		this.validator = validator;
		this.extensions = extensions;
		this.stageResource = stageResource;
	}

	/** Laravel'de asamanin bu hatta ait oldugu kontrol edilmiyor (baska hattin asamasi da senkronlanir). */
	@PutMapping("/api/production-lines/{productionLine}/stages/{stage}/extensions")
	@RequiresPermission("production_stage.manage")
	@Transactional
	DataResponse<Map<String, Object>> sync(
		@Bound(table = "production_lines", model = "ProductionLine") Row productionLine,
		@Bound(table = "production_stages", model = "ProductionStage") Row stage, RequestInput input) {
		ValidatedInput v = validator.validate(input, ProductionStageExtensionRequests.sync());
		extensions.sync(stage.id(), ProductionStageExtensionService.items(v.get("extensions")));
		return DataResponse.of(stageResource.stages(List.of(db.find("production_stages", stage.id()).orElseThrow()), true, true).getFirst());
	}

}
