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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** Prosesin bir alaninin degeri: alanin veri tipine gore yalnizca bir {@code value_*} dolu. */
@Entity
@Table(name = "batch_process_field_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BatchProcessFieldValue extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long batchProcessId;

	@Column(nullable = false)
	private Long processTypeFieldId;

	private String valueText;

	private BigDecimal valueDecimal;

	private Long valueInteger;

	private Boolean valueBoolean;

	private LocalDateTime valueDatetime;

	@JdbcTypeCode(SqlTypes.JSON)
	private JsonNode valueJson;

	BatchProcessFieldValue(long batchProcessId, long processTypeFieldId, Value value) {
		this.batchProcessId = batchProcessId;
		this.processTypeFieldId = processTypeFieldId;
		apply(value);
	}

	/** Yazilacak deger. */
	record Value(String text, BigDecimal decimal, Long integer, Boolean bool, LocalDateTime datetime, JsonNode json) {
	}

	void apply(Value value) {
		valueText = value.text();
		valueDecimal = value.decimal() == null ? null : value.decimal().setScale(4, RoundingMode.HALF_UP);
		valueInteger = value.integer();
		valueBoolean = value.bool();
		valueDatetime = value.datetime();
		valueJson = value.json();
	}

	boolean isFilled() {
		return valueText != null || valueDecimal != null || valueInteger != null || valueBoolean != null
			|| valueDatetime != null || valueJson != null;
	}

	/** Dolu olan deger. */
	Object resolved() {
		if (valueBoolean != null) {
			return valueBoolean;
		}
		if (valueInteger != null) {
			return valueInteger;
		}
		if (valueDecimal != null) {
			return valueDecimal.toPlainString();
		}
		if (valueDatetime != null) {
			return valueDatetime;
		}
		return valueJson != null ? valueJson : valueText;
	}

}
