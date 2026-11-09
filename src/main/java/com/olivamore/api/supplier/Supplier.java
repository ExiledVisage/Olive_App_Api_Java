package com.olivamore.api.supplier;

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

@Entity
@Table(name = "suppliers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Supplier extends AuditedEntity {

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

	@OneToMany(mappedBy = "supplier", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
	@OrderBy("id")
	private List<SupplierField> fields = new ArrayList<>();

	Supplier(CreateSupplierRequest request) {
		name = request.name();
		code = request.code();
		phone = request.phone();
		email = request.email();
		address = request.address();
		note = request.note();
		if (request.isActive() != null) {
			active = request.isActive();
		}
		if (request.fields() != null) {
			request.fields().forEach(field -> fields.add(new SupplierField(this, field)));
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateSupplierRequest request) {
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
