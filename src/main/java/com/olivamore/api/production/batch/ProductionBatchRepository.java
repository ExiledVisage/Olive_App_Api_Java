package com.olivamore.api.production.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long>,
	JpaSpecificationExecutor<ProductionBatch> {

	boolean existsByBatchCode(String batchCode);

	boolean existsByBatchCodeAndIdNot(String batchCode, long id);

	/** Bolme sirasinda teslimat kilitlenir: ayni anda iki bolme kalan kiloyu asmasin. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from ProductionBatch b where b.id = :id")
	Optional<ProductionBatch> lockById(long id);

	List<ProductionBatch> findByParentBatchIdOrderByIdAsc(long parentBatchId);

	long countByParentBatchId(long parentBatchId);

}
