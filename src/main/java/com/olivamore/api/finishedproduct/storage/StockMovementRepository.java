package com.olivamore.api.finishedproduct.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

	List<StockMovement> findByStorageIdOrderByMovedAtDescIdDesc(long storageId);

	List<StockMovement> findByOrderIdAndMovementType(long orderId, MovementType movementType);

	long countByStorageId(long storageId);

	long countBySlotId(long slotId);

}
