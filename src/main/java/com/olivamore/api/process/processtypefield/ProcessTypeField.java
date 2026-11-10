package com.olivamore.api.process.processtypefield;

import com.olivamore.api.process.FieldDataType;
import com.olivamore.api.shared.json.Patch;
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

@Entity
@Table(name = "process_type_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProcessTypeField extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long processTypeId;

	@Column(nullable = false)
	private String fieldKey;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	@Convert(converter = FieldDataTypeConverter.class)
	private FieldDataType dataType;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	private String unit;

	@Column(nullable = false)
	private int sortOrder;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@JdbcTypeCode(SqlTypes.JSON)
	private JsonNode options;

	private String defaultValue;

	ProcessTypeField(long processTypeId, CreateProcessTypeFieldRequest request) {
		this.processTypeId = processTypeId;
		fieldKey = request.fieldKey();
		label = request.label();
		dataType = request.dataType();
		required = Boolean.TRUE.equals(request.isRequired());
		unit = request.unit();
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
		options = request.options();
		defaultValue = request.defaultValue();
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateProcessTypeFieldRequest request) {
		if (request.fieldKey() != null) {
			fieldKey = request.fieldKey();
		}
		if (request.label() != null) {
			label = request.label();
		}
		if (request.dataType() != null) {
			dataType = request.dataType();
		}
		if (request.isRequired() != null) {
			required = request.isRequired();
		}
		Patch.ifPresent(request.unit(), value -> unit = value);
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
		Patch.ifPresent(request.options(), value -> options = value);
		Patch.ifPresent(request.defaultValue(), value -> defaultValue = value);
	}

}
