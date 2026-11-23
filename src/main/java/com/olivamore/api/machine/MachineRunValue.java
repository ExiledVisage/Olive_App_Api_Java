package com.olivamore.api.machine;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;

/** Makine adiminda girilen bir olcum; alanin veri tipine gore tek kolon dolar. */
@Entity
@Table(name = "machine_run_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MachineRunValue extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "machine_run_id")
	private MachineRun run;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "machine_type_field_id")
	private MachineTypeField field;

	private BigDecimal valueDecimal;

	private Long valueInteger;

	private String valueText;

	private Boolean valueBoolean;

	MachineRunValue(MachineRun run, MachineTypeField field, JsonNode value) {
		this.run = run;
		this.field = field;
		set(value);
	}

	/** Deger alanin veri tipine gore dogrulanmis gelir. */
	void set(JsonNode value) {
		valueDecimal = null;
		valueInteger = null;
		valueText = null;
		valueBoolean = null;
		switch (field.getDataType()) {
			case "decimal" -> valueDecimal = value.decimalValue();
			case "integer" -> valueInteger = value.longValue();
			case "boolean" -> valueBoolean = value.booleanValue();
			default -> valueText = value.asString();
		}
	}

	JsonNode json() {
		JsonNodeFactory nodes = JsonNodeFactory.instance;
		return switch (field.getDataType()) {
			case "decimal" -> valueDecimal == null ? nodes.nullNode() : nodes.numberNode(valueDecimal.stripTrailingZeros());
			case "integer" -> valueInteger == null ? nodes.nullNode() : nodes.numberNode(valueInteger);
			case "boolean" -> valueBoolean == null ? nodes.nullNode() : nodes.booleanNode(valueBoolean);
			default -> valueText == null ? nodes.nullNode() : nodes.stringNode(valueText);
		};
	}

}
