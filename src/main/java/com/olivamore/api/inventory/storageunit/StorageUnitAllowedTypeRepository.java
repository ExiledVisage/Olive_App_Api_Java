package com.olivamore.api.inventory.storageunit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface StorageUnitAllowedTypeRepository extends JpaRepository<StorageUnitAllowedType, Long> {

	List<StorageUnitAllowedType> findByStorageUnitId(long storageUnitId);

	List<StorageUnitAllowedType> findByStorageUnitIdInOrderByIdAsc(Collection<Long> unitIds);

	long countByStorageUnitId(long storageUnitId);

	boolean existsByStorageUnitIdAndProductionProductTypeId(long storageUnitId, long productionProductTypeId);

	@Modifying
	@Query("""
		delete from StorageUnitAllowedType a where a.storageUnitId = :unitId and a.productionProductTypeId not in :keep
		""")
	void deleteOthers(long unitId, Collection<Long> keep);

}
