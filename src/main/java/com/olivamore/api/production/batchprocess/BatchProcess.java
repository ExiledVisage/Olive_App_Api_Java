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

	/** Kronometre uzantisi: makinenin baslatildigi an. */
	private LocalDateTime machineStartedAt;

	/** Kronometre uzantisi: makinenin bitirildigi an; dolmadan asama tamamlanmaz. */
	private LocalDateTime machineFinishedAt;

	private String note;

	@JdbcTypeCode(SqlTypes.JSON)
	private JsonNode operationData;

	private Long submittedByUserId;

	private LocalDateTime submittedAt;

	private Long approvedByUserId;

	private LocalDateTime approvedAt;

	/** Grupta: bu asama teslimatta yapildi, grup gecti; teslimatin satiri. */
	private Long inheritedFromProcessId;

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

	void changeMachineStartedAt(LocalDateTime value) {
		machineStartedAt = value;
	}

	void changeMachineFinishedAt(LocalDateTime value) {
		machineFinishedAt = value;
	}

	/** Kronometre baslar; asama da baslamis (in_progress) olur. */
	void startMachine(LocalDateTime now) {
		machineStartedAt = now;
		machineFinishedAt = null;
		begin(now);
	}

	void finishMachine(LocalDateTime now) {
		machineFinishedAt = now;
	}

	/** Teslimatta yapilmis asama: grupta baslamadan gecilir (grup bununla baslamis sayilmaz). */
	public void inherit(long deliveryProcessId, LocalDateTime now) {
		status = ProcessStatus.SKIPPED;
		inheritedFromProcessId = deliveryProcessId;
		completedAt = now;
	}

	/** Teslimatin zeytini tamamen gruplara ayrildi: bekleyen asama islenecek bir sey tasimaz. */
	public void skipUnneeded(LocalDateTime now) {
		status = ProcessStatus.SKIPPED;
		completedAt = now;
	}

	/** {@link #skipUnneeded} ile atlanmis (kullanici atlamasi baslama zamani yazar). */
	public boolean isSkippedAsUnneeded() {
		return status == ProcessStatus.SKIPPED && startedAt == null && inheritedFromProcessId == null;
	}

	/** Teslimata zeytin dondu: kendiliginden atlanan asama yeniden bekler. */
	public void reopenUnneeded() {
		status = ProcessStatus.WAITING;
		completedAt = null;
	}

	void reopen() {
		status = ProcessStatus.WAITING;
		inheritedFromProcessId = null;
		startedAt = null;
		completedAt = null;
		machineStartedAt = null;
		machineFinishedAt = null;
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
