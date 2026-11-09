package com.olivamore.api.finishedproduct.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface StorageStockRepository extends JpaRepository<StorageStock, Long> {

	List<StorageStock> findByStorageIdInOrderByIdAsc(Collection<Long> storageIds);

	boolean existsByStorageIdAndQuantityGreaterThan(long storageId, int quantity);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from StorageStock s where s.storageId = :storageId and s.finishedProductId = :productId")
	Optional<StorageStock> findForUpdate(long storageId, long productId);

	@Query("select coalesce(sum(s.quantity), 0) from StorageStock s where s.finishedProductId = :productId")
	long storedTotal(long productId);

}
