package com.olivamore.api.productionline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.olivamore.api.process.ProcessTypeResponse;

import java.time.LocalDateTime;
import java.util.List;

/** Uretim asamasinin API cevabi. {@code extensions} istenmediyse cevapta yok. */
public record ProductionStageResponse(
	Long id,
	Long productionLineId,
	Long processTypeId,
	String name,
	int stageOrder,
	boolean isRequired,
	boolean requiresApproval,
	ProcessTypeResponse processType,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	List<StageExtensionResponse> extensions,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {
}
