package com.olivamore.api.production.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long>,
	JpaSpecificationExecutor<ProductionBatch> {

	boolean existsByBatchCode(String batchCode);

	boolean existsByBatchCodeAndIdNot(String batchCode, long id);

}
