package com.olivamore.api.machine;

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

/** Makinenin bagimsiz calisan birimi (malaksor haznesi gibi); calismalar parcaya yazilir. */
@Entity
@Table(name = "machine_parts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MachinePart extends AuditedEntity {

	/** Parca verilmeden acilan makinenin tek parcasi. */
	static final String SINGLE_CODE = "1";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "machine_id")
	private Machine machine;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int sortOrder = 1;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	MachinePart(Machine machine, String code, String name, int sortOrder) {
		this.machine = machine;
		this.code = code;
		this.name = name;
		this.sortOrder = sortOrder;
	}

	MachinePart(Machine machine, CreateMachinePartRequest request) {
		this(machine, request.code(), request.name(), request.sortOrder() == null ? 1 : request.sortOrder());
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateMachinePartRequest request) {
		if (request.code() != null) {
			code = request.code();
		}
		if (request.name() != null) {
			name = request.name();
		}
		if (request.sortOrder() != null) {
			sortOrder = request.sortOrder();
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
