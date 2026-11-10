package com.olivamore.api.product;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bu modulun kayitlarini kullanan moduller bunu uygular: silme kontrolu baska modullerin tablolarina
 * bakmaz ve bagimlilik yonu tersine donmez.
 */
public interface ProductUsageCounter {

	/** @param attributeMappingIds kategorinin attribute eslemeleri */
	default Map<ProductUsage, Long> ofProduct(long productId, Collection<Long> attributeMappingIds) {
		return Map.of();
	}

	default Map<ProductUsage, Long> ofProductType(long typeId) {
		return Map.of();
	}

	default Map<ProductUsage, Long> ofAttributeMapping(long mappingId) {
		return Map.of();
	}

	/** Butun modullerin sayimlari toplanmis. */
	static Map<ProductUsage, Long> total(List<ProductUsageCounter> counters,
		Function<ProductUsageCounter, Map<ProductUsage, Long>> count) {
		Map<ProductUsage, Long> total = new EnumMap<>(ProductUsage.class);
		counters.forEach(counter -> count.apply(counter)
			.forEach((usage, value) -> total.merge(usage, value, Long::sum)));
		return total;
	}

}
