package com.olivamore.api.machine;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
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

import java.math.BigDecimal;

/**
 * Makine tipinin alani: {@code run} her calismada girilen olcum (sicaklik, devir...), {@code machine} makine
 * kaydinda duran bilgi (kirici tipi...). Sayisal alanlarda istege bagli min/max.
 */
@Entity
@Table(name = "machine_type_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MachineTypeField extends AuditedEntity {

	static final String RUN = "run";
	static final String MACHINE = "machine";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "machine_type_id")
	private MachineType machineType;

	@Column(nullable = false)
	private String fieldKey;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private String dataType;

	@Column(nullable = false)
	private String scope = RUN;

	private String unit;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(nullable = false)
	private int sortOrder = 1;

	private BigDecimal minValue;

	private BigDecimal maxValue;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	MachineTypeField(MachineType machineType, CreateMachineTypeFieldRequest request) {
		this.machineType = machineType;
		fieldKey = request.fieldKey();
		label = request.label();
		dataType = request.dataType();
		if (request.scope() != null) {
			scope = request.scope();
		}
		unit = request.unit();
		required = Boolean.TRUE.equals(request.isRequired());
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
		minValue = request.minValue();
		maxValue = request.maxValue();
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez; anahtar, veri tipi ve kapsam degismez (girilmis degerler onlara gore). */
	void apply(UpdateMachineTypeFieldRequest request) {
		if (request.label() != null) {
			label = request.label();
		}
		Patch.ifPresent(request.unit(), value -> unit = value);
		if (request.isRequired() != null) {
			required = request.isRequired();
		}
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
		Patch.ifPresent(request.minValue(), value -> minValue = value);
		Patch.ifPresent(request.maxValue(), value -> maxValue = value);
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	boolean forMachine() {
		return MACHINE.equals(scope);
	}

	boolean numeric() {
		return "decimal".equals(dataType) || "integer".equals(dataType);
	}

}
