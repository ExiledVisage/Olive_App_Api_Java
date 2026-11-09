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

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "customer_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAddress extends AuditedEntity {

	private static final String DEFAULT_COUNTRY = "TR";
	private static final int COORDINATE_SCALE = 7;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long customerId;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private String country;

	@Column(nullable = false)
	private String city;

	private String district;

	private String neighborhood;

	private String street;

	private String buildingNo;

	private String apartmentNo;

	private String postalCode;

	private String fullAddress;

	private BigDecimal latitude;

	private BigDecimal longitude;

	@Column(name = "is_default", nullable = false)
	private boolean defaultAddress;

	CustomerAddress(long customerId, CreateCustomerAddressRequest request) {
		this.customerId = customerId;
		label = request.label();
		country = request.country() != null ? request.country() : DEFAULT_COUNTRY;
		city = request.city();
		district = request.district();
		neighborhood = request.neighborhood();
		street = request.street();
		buildingNo = request.buildingNo();
		apartmentNo = request.apartmentNo();
		postalCode = request.postalCode();
		fullAddress = request.fullAddress();
		latitude = coordinate(request.latitude());
		longitude = coordinate(request.longitude());
		defaultAddress = Boolean.TRUE.equals(request.isDefault());
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateCustomerAddressRequest request) {
		if (request.label() != null) {
			label = request.label();
		}
		if (request.country() != null) {
			country = request.country();
		}
		if (request.city() != null) {
			city = request.city();
		}
		Patch.ifPresent(request.district(), value -> district = value);
		Patch.ifPresent(request.neighborhood(), value -> neighborhood = value);
		Patch.ifPresent(request.street(), value -> street = value);
		Patch.ifPresent(request.buildingNo(), value -> buildingNo = value);
		Patch.ifPresent(request.apartmentNo(), value -> apartmentNo = value);
		Patch.ifPresent(request.postalCode(), value -> postalCode = value);
		Patch.ifPresent(request.fullAddress(), value -> fullAddress = value);
		Patch.ifPresent(request.latitude(), value -> latitude = coordinate(value));
		Patch.ifPresent(request.longitude(), value -> longitude = coordinate(value));
		if (request.isDefault() != null) {
			defaultAddress = request.isDefault();
		}
	}

	void markDefault() {
		defaultAddress = true;
	}

	private static BigDecimal coordinate(BigDecimal value) {
		return value == null ? null : value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
	}

}
