package com.olivamore.api.inventory;

import com.olivamore.api.inventory.material.MaterialResource;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MaterialResource'un diger modullere acik kismi (ornek: ProductionBatchResource'taki materyaller,
 * BatchProcessResource'taki created/closed materyaller).
 */
@Component
public class MaterialResources {

	/** Yuklenen iliskiler. */
	public enum Load {

		/** productionProductType.productionProduct + attributeValues.definition */
		TYPE_AND_ATTRIBUTES(MaterialResource.MaterialLoad.TYPE_AND_ATTRIBUTES),

		/** + productionBatch, parentMaterial, createdByBatchProcess, closedByBatchProcess */
		FULL(MaterialResource.MaterialLoad.FULL);

		private final MaterialResource.MaterialLoad load;

		Load(MaterialResource.MaterialLoad load) {
			this.load = load;
		}

	}

	private final MaterialResource reads;

	MaterialResources(MaterialResource reads) {
		this.reads = reads;
	}

	/** Satirlarin sirasiyla MaterialResource'lar. */
	public List<Map<String, Object>> materials(List<Row> materials, Load load) {
		return reads.materials(materials, load.load);
	}

}
