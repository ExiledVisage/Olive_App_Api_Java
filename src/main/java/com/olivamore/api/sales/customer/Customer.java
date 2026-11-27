package com.olivamore.api.sales.customer;

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

@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String companyName;

	private String phone;

	private String email;

	private String notes;

	/** VKN (10 hane) ya da TCKN (11 hane); faturada kullanilir. */
	private String taxNumber;

	private String taxOffice;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	Customer(CreateCustomerRequest request) {
		name = request.name();
		companyName = request.companyName();
		phone = request.phone();
		email = request.email();
		notes = request.notes();
		taxNumber = request.taxNumber();
		taxOffice = request.taxOffice();
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateCustomerRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		Patch.ifPresent(request.companyName(), value -> companyName = value);
		Patch.ifPresent(request.phone(), value -> phone = value);
		Patch.ifPresent(request.email(), value -> email = value);
		Patch.ifPresent(request.notes(), value -> notes = value);
		Patch.ifPresent(request.taxNumber(), value -> taxNumber = value);
		Patch.ifPresent(request.taxOffice(), value -> taxOffice = value);
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
