package com.olivamore.api.analytics.dailysummary;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** Acilis ekrani; kullanicinin izni olmayan bolumler govdede yer almaz. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record DailySummaryResponse(String date, Production production, Stages stages, FillingRuns fillingRuns, Orders orders,
	Stock stock) {

	record Production(List<BatchRow> enteredToday, List<BatchRow> inProduction, List<BatchRow> completedToday) {
	}

	record BatchRow(long id, String batchCode, String status, Double quantityKg, String supplier, String productionLine,
		LocalDateTime receivedAt, LocalDateTime startedAt, LocalDateTime completedAt, CurrentStage currentStage) {
	}

	record CurrentStage(Long batchProcessId, String name, Integer order, String status, int doneCount, int totalCount) {
	}

	record Stages(long completedToday, List<AwaitingProcess> awaitingApproval) {
	}

	record AwaitingProcess(long id, Long productionBatchId, String batchCode, String stageName,
		LocalDateTime submittedAt) {
	}

	record FillingRuns(List<RunRow> completedToday, List<RunRow> inProgress, List<RunRow> planned) {
	}

	record RunRow(long id, String status, long quantity, Long finishedProductId, String finishedProduct,
		Double volumeLitre, String sourceStorageUnit, LocalDateTime startedAt, LocalDateTime completedAt) {
	}

	record Orders(List<OrderRow> completedToday, List<OrderRow> open, List<OrderRow> plannedToday) {
	}

	record OrderRow(long id, String status, String customer, String orderDate, long itemCount) {
	}

	record Stock(List<OutOfStock> outOfStock, List<Unplaced> unplaced) {
	}

	record OutOfStock(long id, String name, long totalStock) {
	}

	record Unplaced(long id, String name, long totalStock, long unstoredStock) {
	}

}
