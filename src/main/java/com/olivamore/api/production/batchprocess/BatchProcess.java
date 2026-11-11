package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** Partinin bir asamasi. */
@Entity
@Table(name = "batch_processes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchProcess extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionBatchId;

	@Column(nullable = false)
	private Long productionStageId;

	@Column(nullable = false)
	@Convert(converter = ProcessStatus.StatusConverter.class)
	private ProcessStatus status = ProcessStatus.WAITING;

	private Long operatorId;

	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	private String note;

	@JdbcTypeCode(SqlTypes.JSON)
	private JsonNode operationData;

	private Long submittedByUserId;

	private LocalDateTime submittedAt;

	private Long approvedByUserId;

	private LocalDateTime approvedAt;

	public BatchProcess(long productionBatchId, long productionStageId) {
		this.productionBatchId = productionBatchId;
		this.productionStageId = productionStageId;
	}

	void changeOperator(Long value) {
		operatorId = value;
	}

	void changeNote(String value) {
		note = value;
	}

	void changeStartedAt(LocalDateTime value) {
		startedAt = value;
	}

	void changeCompletedAt(LocalDateTime value) {
		completedAt = value;
	}

	void reopen() {
		status = ProcessStatus.WAITING;
		startedAt = null;
		completedAt = null;
		operationData = null;
	}

	void begin(LocalDateTime now) {
		markStarted(now);
		status = ProcessStatus.IN_PROGRESS;
		completedAt = null;
	}

	void markStarted(LocalDateTime now) {
		if (startedAt == null) {
			startedAt = now;
		}
	}

	void finish(JsonNode data, LocalDateTime now) {
		operationData = data;
		close(ProcessStatus.COMPLETED, now);
	}

	/** On dinlendirme: urun tankta, asama tank bosaltilana kadar acik bekler. */
	void rest(JsonNode data) {
		operationData = data;
		status = ProcessStatus.RESTING;
		completedAt = null;
	}

	void skip(LocalDateTime now) {
		markStarted(now);
		close(ProcessStatus.SKIPPED, now);
	}

	void fail(LocalDateTime now) {
		markStarted(now);
		close(ProcessStatus.FAILED, now);
	}

	private void close(ProcessStatus closed, LocalDateTime now) {
		status = closed;
		if (completedAt == null) {
			completedAt = now;
		}
	}

	void submit(JsonNode data, Long userId, LocalDateTime now) {
		operationData = data;
		status = ProcessStatus.AWAITING_APPROVAL;
		submittedByUserId = userId;
		submittedAt = now;
	}

	void approve(long userId, LocalDateTime now) {
		status = ProcessStatus.COMPLETED;
		completedAt = now;
		approvedByUserId = userId;
		approvedAt = now;
	}

	/** Gonderilmis veri korunur ki isci duzeltirken uzerinden calissin. */
	void reject() {
		status = ProcessStatus.IN_PROGRESS;
		submittedByUserId = null;
		submittedAt = null;
	}

}
