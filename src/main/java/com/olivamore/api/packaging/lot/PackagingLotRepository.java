package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.lot.LotEnums.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PackagingLotRepository extends JpaRepository<PackagingLot, Long>, JpaSpecificationExecutor<PackagingLot> {

	@Override
	@EntityGraph(attributePaths = {"item", "supplier"})
	Optional<PackagingLot> findById(Long id);

	@Override
	@EntityGraph(attributePaths = {"item", "supplier"})
	List<PackagingLot> findAll(Specification<PackagingLot> specification, Sort sort);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from PackagingLot l join fetch l.item where l.id = :id")
	Optional<PackagingLot> findForUpdate(long id);

	@EntityGraph(attributePaths = "item")
	List<PackagingLot> findByIdIn(Collection<Long> ids);

	List<PackagingLot> findByItemIdAndStatusAndQuantityRemainingGreaterThanOrderByReceivedAtAscIdAsc(long itemId,
		Status status, int quantity);

	boolean existsByItemId(long itemId);

	boolean existsByItemIdAndLotCode(long itemId, String lotCode);

	boolean existsByItemIdAndLotCodeAndIdNot(long itemId, String lotCode, long id);

	@Query("select coalesce(sum(l.quantityRemaining), 0) from PackagingLot l where l.item.id = :itemId")
	int sumRemaining(long itemId);

	@Query("""
		select coalesce(sum(l.quantityRemaining), 0) from PackagingLot l
		where l.item.id = :itemId and l.status = :status
		""")
	long sumRemaining(long itemId, Status status);

}
