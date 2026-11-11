package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.inventory.material.MaterialUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StorageUnitBalanceRepository extends JpaRepository<StorageUnitBalance, Long> {

	List<StorageUnitBalance> findByStorageUnitIdOrderByIdAsc(long storageUnitId);

	List<StorageUnitBalance> findByStorageUnitIdInOrderByIdAsc(Collection<Long> unitIds);

	@Query("""
		select b from StorageUnitBalance b
		where b.storageUnitId = :unitId and b.productionProductId = :productId and b.unit = :unit
		""")
	Optional<StorageUnitBalance> find(long unitId, Long productId, MaterialUnit unit);

	/** Ayni islemde ayni anahtarla yeni satir yazilabildigi icin sorguyla ve hemen silinir. */
	@Modifying(flushAutomatically = true)
	@Query("""
		delete from StorageUnitBalance b
		where b.storageUnitId = :unitId and b.productionProductId = :productId and b.unit = :unit
		""")
	void deleteForTank(long unitId, Long productId, MaterialUnit unit);

}
