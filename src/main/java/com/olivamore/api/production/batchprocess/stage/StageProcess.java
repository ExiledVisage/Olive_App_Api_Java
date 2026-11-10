package com.olivamore.api.production.batchprocess.stage;

/** Isleyicilerin gordugu proses: {@code note} bu istekte yazilmis deger. */
public record StageProcess(long id, long productionBatchId, long productionStageId, String note) {
}
