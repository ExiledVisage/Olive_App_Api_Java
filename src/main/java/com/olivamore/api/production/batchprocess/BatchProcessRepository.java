package com.olivamore.api.production.batchprocess;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BatchProcessRepository extends JpaRepository<BatchProcess, Long> {

	List<BatchProcess> findByProductionBatchId(long batchId);

	List<BatchProcess> findByProductionBatchIdInOrderByIdAsc(Collection<Long> batchIds);

	List<BatchProcess> findByProductionBatchIdAndIdNot(long batchId, long id);

	long countByProductionBatchIdAndStatusNot(long batchId, ProcessStatus status);

	/** Bu on dinlendirme tankinda dinlenmedeki prosesler (girisin {@code storage_unit_id}'si), giris sirasiyla. */
	@Query(value = "select * from batch_processes where status = 'resting'"
		+ " and (operation_data ->> 'storage_unit_id')::bigint = :unit order by approved_at, id", nativeQuery = true)
	List<BatchProcess> findRestingIn(@Param("unit") long unitId);

}
