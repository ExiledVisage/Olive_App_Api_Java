package com.olivamore.api.supplier;

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
import java.math.RoundingMode;

/** Tedarikcinin tarlasi. */
@Entity
@Table(name = "supplier_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SupplierField extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;

	@Column(nullable = false)
	private String code;

	private String location;

	private BigDecimal areaDecare;

	private String note;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	SupplierField(Supplier supplier, CreateSupplierFieldRequest request) {
		this.supplier = supplier;
		code = request.code();
		location = request.location();
		areaDecare = area(request.areaDecare());
		note = request.note();
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateSupplierFieldRequest request) {
		if (request.code() != null) {
			code = request.code();
		}
		Patch.ifPresent(request.location(), value -> location = value);
		Patch.ifPresent(request.areaDecare(), value -> areaDecare = area(value));
		Patch.ifPresent(request.note(), value -> note = value);
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	private static BigDecimal area(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

}
