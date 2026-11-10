package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
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

/** Onay ya da red kaydi: o anki gonderilmis verinin kopyasi. */
@Entity
@Table(name = "batch_process_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BatchProcessReview extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long batchProcessId;

	@Column(nullable = false)
	private String decision;

	private Long reviewedByUserId;

	private Long submittedByUserId;

	@JdbcTypeCode(SqlTypes.JSON)
	private JsonNode operationData;

	private String note;

	@Column(nullable = false)
	private LocalDateTime reviewedAt;

	BatchProcessReview(long batchProcessId, String decision, long reviewedByUserId, Long submittedByUserId,
		JsonNode operationData, String note, LocalDateTime reviewedAt) {
		this.batchProcessId = batchProcessId;
		this.decision = decision;
		this.reviewedByUserId = reviewedByUserId;
		this.submittedByUserId = submittedByUserId;
		this.operationData = operationData;
		this.note = note;
		this.reviewedAt = reviewedAt;
	}

}
