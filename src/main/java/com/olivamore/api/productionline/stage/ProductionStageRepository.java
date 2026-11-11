package com.olivamore.api.productionline.stage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

interface ProductionStageRepository extends JpaRepository<ProductionStage, Long> {

	List<ProductionStage> findByProductionLineIdOrderByStageOrderAscIdAsc(long lineId);

	List<ProductionStage> findByProductionLineIdInOrderByStageOrderAscIdAsc(Collection<Long> lineIds);

	@Query("select coalesce(max(s.stageOrder), 0) from ProductionStage s where s.productionLineId = :lineId")
	int maxOrder(long lineId);

	@Query("select count(l) > 0 from ProductionLine l where l.id = :lineId")
	boolean lineExists(long lineId);

	@Modifying
	@Query("""
		update ProductionStage s set s.stageOrder = s.stageOrder + :by, s.updatedAt = :now
		where s.productionLineId = :lineId and s.stageOrder between :start and :end
		""")
	void moveOrders(long lineId, int start, int end, int by, LocalDateTime now);

	@Query(value = "select count(*) from batch_processes where production_stage_id = :id", nativeQuery = true)
	long countBatchProcesses(long id);

}
