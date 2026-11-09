package com.olivamore.api.productionline.stageextension;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface ExtensionRepository extends JpaRepository<ProductionStageExtension, Long> {

	List<ProductionStageExtension> findByProductionStageIdInOrderBySortOrderAscIdAsc(Collection<Long> stageIds);

	/** Hemen calisir; degerler ve urunler veritabaninda cascade ile silinir. */
	@Modifying
	@Query("delete from ProductionStageExtension e where e.productionStageId = :stageId")
	void deleteForStage(long stageId);

}
