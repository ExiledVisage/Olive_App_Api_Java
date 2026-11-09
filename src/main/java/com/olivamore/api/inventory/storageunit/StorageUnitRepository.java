package com.olivamore.api.inventory.storageunit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface StorageUnitRepository extends JpaRepository<StorageUnit, Long> {

	/** Satir kilidi; id sirasiyla (kilitlenme olmasin). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from StorageUnit u where u.id in :ids order by u.id")
	List<StorageUnit> findAllForUpdate(Collection<Long> ids);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);

}
