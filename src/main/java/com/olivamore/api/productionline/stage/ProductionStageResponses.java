package com.olivamore.api.productionline.stage;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.process.ProcessTypeResponse;
import com.olivamore.api.productionline.ProductionStageResponse;
import com.olivamore.api.productionline.StageExtensionResponse;
import com.olivamore.api.productionline.stageextension.StageExtensionResponses;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Entity'den API cevabina. */
@Component
public class ProductionStageResponses {

	private final ProductionStageRepository repository;
	private final ProcessResources process;
	private final StageExtensionResponses extensionResponses;

	ProductionStageResponses(ProductionStageRepository repository, ProcessResources process,
		StageExtensionResponses extensionResponses) {
		this.repository = repository;
		this.process = process;
		this.extensionResponses = extensionResponses;
	}

	/** Hatta gore, stage_order sirasinda. */
	@Transactional(readOnly = true)
	public Map<Long, List<ProductionStageResponse>> byLine(List<Long> lineIds, boolean extensions) {
		return of(repository.findByProductionLineIdInOrderByStageOrderAscIdAsc(lineIds), extensions).stream()
			.collect(Collectors.groupingBy(ProductionStageResponse::productionLineId));
	}

	/** Uzantilariyla. */
	@Transactional(readOnly = true)
	public ProductionStageResponse one(long stageId) {
		ProductionStage stage = repository.findById(stageId)
			.orElseThrow(() -> new ModelNotFoundException("ProductionStage", stageId));
		return of(List.of(stage), true).getFirst();
	}

	List<ProductionStageResponse> of(List<ProductionStage> stages, boolean extensions) {
		Map<Long, ProcessTypeResponse> types =
			process.processTypesById(stages.stream().map(ProductionStage::getProcessTypeId).toList());
		Map<Long, List<StageExtensionResponse>> byStage = extensions
			? extensionResponses.byStage(stages.stream().map(ProductionStage::getId).toList(), true)
			: Map.of();
		return stages.stream().map(stage -> new ProductionStageResponse(stage.getId(), stage.getProductionLineId(),
			stage.getProcessTypeId(), stage.getName(), stage.getStageOrder(), stage.isRequired(),
			stage.isRequiresApproval(), stage.getProcessTypeId() == null ? null : types.get(stage.getProcessTypeId()),
			extensions ? byStage.getOrDefault(stage.getId(), List.of()) : null, stage.getCreatedAt(),
			stage.getUpdatedAt())).toList();
	}

}
