package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.inventory.material.Quantities;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Optional;

/** Tank; kapasitesi 2 basamak. */
@Entity
@Table(name = "storage_units")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageUnit extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private BigDecimal capacity;

	@Column(nullable = false)
	@Convert(converter = MaterialUnit.UnitConverter.class)
	private MaterialUnit capacityUnit;

	@Column(nullable = false)
	@Convert(converter = StorageUnitKind.KindConverter.class)
	private StorageUnitKind kind = StorageUnitKind.FINAL;

	@Column(name = "is_blend_tank", nullable = false)
	private boolean blendTank;

	@Column(name = "allow_multiple_production_product_types", nullable = false)
	private boolean allowMultipleTypes;

	@Column(name = "allow_multiple_production_products", nullable = false)
	private boolean allowMultipleProducts;

	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	/** Seviye olcumu icin geometri; hepsi bossa tankin geometrisi girilmemis. */
	private BigDecimal innerDiameterMm;

	private BigDecimal cylinderHeightMm;

	private BigDecimal bottomConeDeg;

	private BigDecimal topConeDeg;

	private BigDecimal rulerOffsetMm;

	private BigDecimal deadVolumeLitre;

	StorageUnit(CreateStorageUnitRequest request) {
		name = request.name();
		code = request.code();
		capacity = Quantities.balance(request.capacity());
		capacityUnit = request.capacityUnit();
		kind = request.kind() == null ? StorageUnitKind.FINAL : request.kind();
		blendTank = Boolean.TRUE.equals(request.isBlendTank());
		allowMultipleTypes = Boolean.TRUE.equals(request.allowMultipleProductionProductTypes());
		allowMultipleProducts = Boolean.TRUE.equals(request.allowMultipleProductionProducts());
		description = request.description();
		active = request.isActive() == null || request.isActive();
	}

	/** Yalnizca gonderilen alanlar. */
	/** Girilmediyse bos. */
	public Optional<TankGeometry> geometry() {
		return innerDiameterMm == null || cylinderHeightMm == null ? Optional.empty()
			: Optional.of(new TankGeometry(innerDiameterMm, cylinderHeightMm, bottomConeDeg, topConeDeg,
				rulerOffsetMm, deadVolumeLitre));
	}

	/** {@code null} geometriyi siler. */
	public void changeGeometry(TankGeometry geometry) {
		innerDiameterMm = geometry == null ? null : geometry.innerDiameterMm();
		cylinderHeightMm = geometry == null ? null : geometry.cylinderHeightMm();
		bottomConeDeg = geometry == null ? null : geometry.bottomConeDeg();
		topConeDeg = geometry == null ? null : geometry.topConeDeg();
		rulerOffsetMm = geometry == null ? null : geometry.rulerOffsetMm();
		deadVolumeLitre = geometry == null ? null : geometry.deadVolumeLitre();
	}

	void apply(UpdateStorageUnitRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		if (request.code() != null) {
			code = request.code();
		}
		if (request.capacity() != null) {
			capacity = Quantities.balance(request.capacity());
		}
		if (request.capacityUnit() != null) {
			capacityUnit = request.capacityUnit();
		}
		if (request.kind() != null) {
			kind = request.kind();
		}
		if (request.isBlendTank() != null) {
			blendTank = request.isBlendTank();
		}
		if (request.allowMultipleProductionProductTypes() != null) {
			allowMultipleTypes = request.allowMultipleProductionProductTypes();
		}
		if (request.allowMultipleProductionProducts() != null) {
			allowMultipleProducts = request.allowMultipleProductionProducts();
		}
		Patch.ifPresent(request.description(), value -> description = value);
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

}
