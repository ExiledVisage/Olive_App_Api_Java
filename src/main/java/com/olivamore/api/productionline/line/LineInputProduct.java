package com.olivamore.api.productionline.line;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "production_line_input_production_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LineInputProduct extends LineProduct {

	LineInputProduct(long lineId, LineProductRequest request, int defaultOrder) {
		super(lineId, request, defaultOrder);
	}

}
