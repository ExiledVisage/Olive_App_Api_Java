package com.olivamore.api.finishedproduct.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SlotStockRepository extends JpaRepository<SlotStock, Long> {

	List<SlotStock> findBySlotIdInOrderByIdAsc(Collection<Long> slotIds);

	List<SlotStock> findBySlotIdOrderByIdAsc(long slotId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SlotStock s where s.slotId = :slotId and s.finishedProductId = :productId")
	Optional<SlotStock> findForUpdate(long slotId, long productId);

}
