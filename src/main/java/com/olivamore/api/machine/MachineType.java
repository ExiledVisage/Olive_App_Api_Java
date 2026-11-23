package com.olivamore.api.machine;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Makine tipi (Kirici, Malaksor, Dekantor, Separator): islevi ve calismada girilen olcum alanlari. */
@Entity
@Table(name = "machine_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MachineType extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String key;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(nullable = false)
	private int sortOrder = 1;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@OneToMany(mappedBy = "machineType", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
	@OrderBy("sortOrder, id")
	private List<MachineTypeField> fields = new ArrayList<>();

	MachineType(CreateMachineTypeRequest request) {
		key = request.key();
		name = request.name();
		description = request.description();
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
		if (request.fields() != null) {
			request.fields().forEach(field -> fields.add(new MachineTypeField(this, field)));
		}
	}

	/** Gelmeyen alanlar degismez; anahtar degismez (kodda ve kayitlarda gecer). */
	void apply(UpdateMachineTypeRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		Patch.ifPresent(request.description(), value -> description = value);
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
