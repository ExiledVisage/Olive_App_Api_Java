package com.olivamore.api.inventory.storageunit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StorageUnitAttributeRuleRepository extends JpaRepository<StorageUnitAttributeRule, Long> {

	List<StorageUnitAttributeRule> findByStorageUnitIdAndActiveTrue(long storageUnitId);

	List<StorageUnitAttributeRule> findByStorageUnitIdInOrderByIdAsc(Collection<Long> unitIds);

	Optional<StorageUnitAttributeRule> findByStorageUnitIdAndDefinitionId(long storageUnitId, long definitionId);

	@Modifying
	@Query("delete from StorageUnitAttributeRule r where r.storageUnitId = :unitId and r.definitionId not in :keep")
	void deleteOthers(long unitId, Collection<Long> keep);

}
