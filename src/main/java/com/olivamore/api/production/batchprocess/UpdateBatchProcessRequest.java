package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.json.Patch;
import tools.jackson.databind.JsonNode;

/** Proses guncelleme: {@code status} varsa durum gecisi; {@code operation_data}'nin sekli asamaya gore serviste. */
record UpdateBatchProcessRequest(
	ProcessStatus status,

	Patch<Long> operatorId,

	Patch<String> startedAt,

	Patch<String> completedAt,

	Patch<String> note,

	JsonNode operationData) {
}
