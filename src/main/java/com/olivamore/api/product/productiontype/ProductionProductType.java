package com.olivamore.api.product.productiontype;

import com.olivamore.api.product.productionproduct.Density;
import com.olivamore.api.product.productionproduct.ProductionProduct;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

@Entity
@Table(name = "production_product_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductionProductType extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "production_product_id")
	private ProductionProduct product;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String code;

	private String type;

	@Column(nullable = false)
	@Convert(converter = ProductUnitConverter.class)
	private ProductUnit unit;

	private BigDecimal densityKgPerLitre;

	ProductionProductType(ProductionProduct product, CreateProductionProductTypeRequest request) {
		this.product = product;
		name = request.name();
		code = request.code();
		type = request.type();
		unit = request.unit();
		densityKgPerLitre = Density.normalize(request.densityKgPerLitre());
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateProductionProductTypeRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		if (request.code() != null) {
			code = request.code();
		}
		if (request.type() != null) {
			type = request.type();
		}
		if (request.unit() != null) {
			unit = request.unit();
		}
		Patch.ifPresent(request.densityKgPerLitre(), value -> densityKgPerLitre = Density.normalize(value));
	}

	void moveTo(ProductionProduct target) {
		product = target;
	}

	/** Kendi yogunlugu pozitifse o, yoksa kategorinin varsayilani pozitifse o. */
	BigDecimal effectiveDensity() {
		if (densityKgPerLitre != null && densityKgPerLitre.signum() > 0) {
			return densityKgPerLitre;
		}
		BigDecimal fallback = product.getDefaultDensityKgPerLitre();
		return fallback != null && fallback.signum() > 0 ? fallback : null;
	}

}
