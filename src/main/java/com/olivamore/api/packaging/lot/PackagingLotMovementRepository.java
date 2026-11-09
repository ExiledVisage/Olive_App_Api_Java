package com.olivamore.api.packaging.lot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface PackagingLotMovementRepository extends JpaRepository<PackagingLotMovement, Long> {

	List<PackagingLotMovement> findByPackagingLotIdInOrderByMovedAtAscIdAsc(Collection<Long> lotIds);

	@Modifying
	@Query("delete from PackagingLotMovement m where m.packagingLotId = :lotId")
	void deleteForLot(long lotId);

}
