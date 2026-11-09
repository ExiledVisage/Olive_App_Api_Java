package com.olivamore.api.packaging.supplier;

import com.olivamore.api.shared.json.Patch;
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

/** Ambalaj ve satin alinan malzemeyi getiren firma. */
@Entity
@Table(name = "product_suppliers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSupplier extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String code;

	private String phone;

	private String email;

	private String address;

	private String note;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	ProductSupplier(CreateProductSupplierRequest request) {
		name = request.name();
		code = request.code();
		phone = request.phone();
		email = request.email();
		address = request.address();
		note = request.note();
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateProductSupplierRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		if (request.code() != null) {
			code = request.code();
		}
		Patch.ifPresent(request.phone(), value -> phone = value);
		Patch.ifPresent(request.email(), value -> email = value);
		Patch.ifPresent(request.address(), value -> address = value);
		Patch.ifPresent(request.note(), value -> note = value);
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
