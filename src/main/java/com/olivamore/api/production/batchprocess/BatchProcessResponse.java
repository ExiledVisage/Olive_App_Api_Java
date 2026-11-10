package com.olivamore.api.production.batchprocess;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.process.ProcessTypeFieldResponse;
import com.olivamore.api.productionline.StageExtensionResponse;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Parti prosesi. {@code requires_approval} ve {@code detail} yalnizca proses detayinda dolu. */
public record BatchProcessResponse(
	Long id,
	Long productionBatchId,
	Long productionStageId,
	Long operatorId,
	ProcessStatus status,
	LocalDateTime startedAt,
	LocalDateTime completedAt,
	String note,
	JsonNode operationData,
	Boolean requiresApproval,
	LocalDateTime submittedAt,
	LocalDateTime approvedAt,
	@JsonUnwrapped Detail detail,
	BatchSummary productionBatch,
	UserSummary operator,
	StageSummary productionStage,
	ProcessTypeSummary processType,
	List<ProcessTypeFieldResponse> fieldDefinitions,
	List<StageExtensionResponse> extensions,
	List<FieldValueResponse> fieldValues,
	List<MaterialResponse> createdMaterials,
	List<MaterialResponse> closedMaterials,
	LocalDateTime createdAt,
	LocalDateTime updatedAt) {

	public record Detail(UserName submittedBy, UserName approvedBy, List<ReviewResponse> reviews) {
	}

	public record UserName(Long id, String name) {
	}

	public record UserSummary(Long id, String name, String email) {
	}

	public record BatchSummary(Long id, String batchCode, String status) {
	}

	public record StageSummary(Long id, String name, int stageOrder, boolean isRequired) {
	}

	public record ProcessTypeSummary(Long id, String name, String description, ProcessCategory category) {
	}

	public record ReviewResponse(Long id, String decision, String note, JsonNode operationData,
		LocalDateTime reviewedAt, UserName reviewedBy, UserName submittedBy) {
	}

	public record FieldValueResponse(Long id, Long batchProcessId, Long processTypeFieldId, String valueText,
		@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valueDecimal, Long valueInteger, Boolean valueBoolean,
		LocalDateTime valueDatetime, JsonNode valueJson, Object resolvedValue, FieldSummary processTypeField) {
	}

	public record FieldSummary(Long id, Long processTypeId, String fieldKey, String label, String dataType,
		boolean isRequired, String unit, Integer sortOrder, boolean isActive) {
	}

}
