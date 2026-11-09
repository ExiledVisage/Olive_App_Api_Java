package com.olivamore.api.finishedproduct.recipe;

import java.math.BigDecimal;

/** Ekleme ve guncelleme isteklerinin ortak icerik ve kural satiri. */
final class RecipeLines {

	interface Content {

		Long productionProductTypeId();

		BigDecimal ratio();

	}

	interface Rule {

		Long attributeDefinitionId();

		BigDecimal value();

		BigDecimal minDeviation();

		BigDecimal maxDeviation();

	}

	private RecipeLines() {
	}

}
