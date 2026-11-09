package com.olivamore.api.process.stageextensiontype;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Alanlar seed ile gelir; API yalnizca okur. */
@Entity
@Table(name = "stage_extension_type_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StageExtensionTypeField extends AuditedEntity {

	@Id
	private Long id;

	@Column(nullable = false)
	private Long stageExtensionTypeId;

	@Column(nullable = false)
	private String fieldKey;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private String dataType;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(nullable = false)
	private int sortOrder;

	private Boolean defaultValueBoolean;

	private Long defaultValueInteger;

	private BigDecimal defaultValueDecimal;

	private String defaultValueText;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	private JsonNode defaultValueJson;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	private JsonNode optionsJson;

	private String description;

}
