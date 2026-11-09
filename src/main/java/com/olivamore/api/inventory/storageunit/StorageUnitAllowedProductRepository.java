package com.olivamore.api.inventory.storageunit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface StorageUnitAllowedProductRepository extends JpaRepository<StorageUnitAllowedProduct, Long> {

	List<StorageUnitAllowedProduct> findByStorageUnitId(long storageUnitId);

	List<StorageUnitAllowedProduct> findByStorageUnitIdIn(Collection<Long> unitIds);

	long countByStorageUnitId(long storageUnitId);

	boolean existsByStorageUnitIdAndProductionProductId(long storageUnitId, long productionProductId);

	@Modifying
	@Query("""
		delete from StorageUnitAllowedProduct a where a.storageUnitId = :unitId and a.productionProductId not in :keep
		""")
	void deleteOthers(long unitId, Collection<Long> keep);

}
