package com.olivamore.api.productionline.stageextension;

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

@Entity
@Table(name = "production_stage_extension_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductionStageExtensionValue extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long productionStageExtensionId;

	@Column(nullable = false)
	private Long stageExtensionTypeFieldId;

	private Boolean valueBoolean;

	private Long valueInteger;

	private BigDecimal valueDecimal;

	private String valueText;

	@JdbcTypeCode(SqlTypes.JSON)
	private JsonNode valueJson;

	ProductionStageExtensionValue(long extensionId, long fieldId, StageExtensionValueRequest request, JsonNode json) {
		productionStageExtensionId = extensionId;
		stageExtensionTypeFieldId = fieldId;
		valueBoolean = request.valueBoolean();
		valueInteger = request.valueInteger();
		valueDecimal = request.valueDecimal();
		valueText = request.valueText();
		valueJson = json;
	}

}
