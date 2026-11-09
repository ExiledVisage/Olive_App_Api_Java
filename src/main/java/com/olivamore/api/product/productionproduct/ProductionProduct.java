package com.olivamore.api.product.productionproduct;

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

@Entity
@Table(name = "production_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionProduct extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String key;

	@Column(nullable = false)
	private String name;

	private String description;

	private BigDecimal defaultDensityKgPerLitre;

	ProductionProduct(CreateProductionProductRequest request) {
		key = request.key();
		name = request.name();
		description = request.description();
		defaultDensityKgPerLitre = Density.normalize(request.defaultDensityKgPerLitre());
	}

	/** Gelmeyen alanlar degismez. */
	void apply(UpdateProductionProductRequest request) {
		if (request.key() != null) {
			key = request.key();
		}
		if (request.name() != null) {
			name = request.name();
		}
		Patch.ifPresent(request.description(), value -> description = value);
		Patch.ifPresent(request.defaultDensityKgPerLitre(),
			value -> defaultDensityKgPerLitre = Density.normalize(value));
	}

}
