package com.olivamore.api.productionline.stageextension;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "production_stage_extension_output_production_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExtensionOutputProduct extends ExtensionProduct {

	ExtensionOutputProduct(long extensionId, long productId, int order) {
		super(extensionId, productId, order);
	}

}
