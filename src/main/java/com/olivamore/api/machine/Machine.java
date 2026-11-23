package com.olivamore.api.machine;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Fabrikadaki makine; parcalari en az bir tane (verilmezse tek parca acilir). */
@Entity
@Table(name = "machines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Machine extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "machine_type_id")
	private MachineType machineType;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	private String brand;

	private String model;

	private BigDecimal capacityKgPerHour;

	private String note;

	/** Tipin makine bilgisi alanlari (field_key -> deger); MachineAttributes denetler. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private ObjectNode attributes;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@OneToMany(mappedBy = "machine", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
	@OrderBy("sortOrder, id")
	private List<MachinePart> parts = new ArrayList<>();

	Machine(MachineType machineType, CreateMachineRequest request, ObjectNode attributes) {
		this.attributes = attributes;
		this.machineType = machineType;
		code = request.code();
		name = request.name();
		brand = request.brand();
		model = request.model();
		capacityKgPerHour = request.capacityKgPerHour();
		note = request.note();
		if (request.isActive() != null) {
			active = request.isActive();
		}
		if (request.parts() == null || request.parts().isEmpty()) {
			parts.add(new MachinePart(this, MachinePart.SINGLE_CODE, name, 1));
		}
		else {
			request.parts().forEach(part -> parts.add(new MachinePart(this, part)));
		}
	}

	/** Gelmeyen alanlar degismez; bilgi alanlari verilirse tumu degisir; tip degismez (calismalari tipin alanlariyla). */
	void apply(UpdateMachineRequest request, ObjectNode attributes) {
		if (attributes != null) {
			this.attributes = attributes;
		}
		if (request.code() != null) {
			code = request.code();
		}
		if (request.name() != null) {
			name = request.name();
		}
		Patch.ifPresent(request.brand(), value -> brand = value);
		Patch.ifPresent(request.model(), value -> model = value);
		Patch.ifPresent(request.capacityKgPerHour(), value -> capacityKgPerHour = value);
		Patch.ifPresent(request.note(), value -> note = value);
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
