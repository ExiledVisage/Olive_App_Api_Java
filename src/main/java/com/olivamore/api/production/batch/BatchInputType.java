package com.olivamore.api.production.batch;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "production_batch_input_production_product_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BatchInputType extends BatchProductType {

	BatchInputType(long productionBatchId, long productionProductTypeId, int sortOrder) {
		super(productionBatchId, productionProductTypeId, sortOrder);
	}

}
