package com.olivamore.api.production.batchprocess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BatchProcessRepository extends JpaRepository<BatchProcess, Long> {

	List<BatchProcess> findByProductionBatchId(long batchId);

	List<BatchProcess> findByProductionBatchIdIn(Collection<Long> batchIds);

	List<BatchProcess> findByProductionBatchIdAndIdNot(long batchId, long id);

	long countByProductionBatchIdAndStatusNot(long batchId, ProcessStatus status);

}
