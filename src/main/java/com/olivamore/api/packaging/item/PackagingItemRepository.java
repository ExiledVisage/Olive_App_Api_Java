package com.olivamore.api.packaging.item;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PackagingItemRepository extends JpaRepository<PackagingItem, Long> {

	interface LotCounts {

		Long getItemId();

		long getTotal();

		long getActive();

	}

	List<PackagingItem> findAllByOrderByCreatedAtDesc();

	List<PackagingItem> findByActiveOrderByCreatedAtDesc(boolean active);

	@Query(value = """
		select packaging_item_id as itemId, count(*) as total,
			count(*) filter (where status = 'active') as active
		from packaging_lots group by packaging_item_id
		""", nativeQuery = true)
	List<LotCounts> countLots();

	@Query(value = "select count(*) from finished_product_components where packaging_item_id = :id", nativeQuery = true)
	long countComponents(long id);

}
