package com.olivamore.api.production.batch;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "production_batch_output_production_product_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BatchOutputType extends BatchProductType {

	BatchOutputType(long productionBatchId, long productionProductTypeId, int sortOrder) {
		super(productionBatchId, productionProductTypeId, sortOrder);
	}

}
