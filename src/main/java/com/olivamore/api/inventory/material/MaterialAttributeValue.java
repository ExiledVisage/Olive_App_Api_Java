package com.olivamore.api.inventory.material;

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

/** Materyalin attribute degeri (tek tanim icin tek satir). */
@Entity
@Table(name = "material_attribute_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialAttributeValue extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long materialId;

	@Column(name = "production_product_attribute_definition_id", nullable = false)
	private Long definitionId;

	private Boolean valueBoolean;

	private Long valueInteger;

	private BigDecimal valueDecimal;

	private String valueText;

	@JdbcTypeCode(SqlTypes.JSON)
	private JsonNode valueJson;

	public MaterialAttributeValue(long materialId, long definitionId, Boolean valueBoolean, Long valueInteger,
		BigDecimal valueDecimal, String valueText, JsonNode valueJson) {
		this.materialId = materialId;
		this.definitionId = definitionId;
		update(valueBoolean, valueInteger, valueDecimal, valueText, valueJson);
	}

	/** Butun degerler verilenle degisir. */
	public void update(Boolean valueBoolean, Long valueInteger, BigDecimal valueDecimal, String valueText,
		JsonNode valueJson) {
		this.valueBoolean = valueBoolean;
		this.valueInteger = valueInteger;
		this.valueDecimal = Quantities.material(valueDecimal);
		this.valueText = valueText;
		this.valueJson = valueJson == null || valueJson.isNull() ? null : valueJson;
	}

}
