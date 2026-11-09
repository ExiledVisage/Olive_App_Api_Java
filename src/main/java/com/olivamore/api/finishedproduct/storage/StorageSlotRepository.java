package com.olivamore.api.finishedproduct.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface StorageSlotRepository extends JpaRepository<StorageSlot, Long> {

	List<StorageSlot> findByStorageIdInOrderByIdAsc(Collection<Long> storageIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from StorageSlot s where s.id = :id")
	Optional<StorageSlot> findForUpdate(long id);

}
