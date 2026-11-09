package com.olivamore.api.inventory;

import com.olivamore.api.inventory.material.MaterialResponses;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Materyal cevaplarinin diger modullere acik kismi (ornek: parti detayindaki materyaller,
 * prosesin yarattigi ve kapattigi materyaller).
 */
@Component
public class MaterialResources {

	/** Yuklenen iliskiler. */
	public enum Load {

		/** urun tipi (kategorisiyle) + attribute degerleri */
		TYPE_AND_ATTRIBUTES,

		/** + parti, ebeveyn, yaratan ve kapatan proses */
		FULL

	}

	private final MaterialResponses responses;

	MaterialResources(MaterialResponses responses) {
		this.responses = responses;
	}

	/** Verilen id'lerin sirasiyla cevaplar. */
	public List<MaterialResponse> materials(List<Long> ids, Load load) {
		return responses.materials(ids, load == Load.FULL);
	}

}
