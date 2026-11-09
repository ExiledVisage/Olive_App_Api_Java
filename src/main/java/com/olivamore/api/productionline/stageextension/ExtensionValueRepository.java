package com.olivamore.api.productionline.stageextension;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface ExtensionValueRepository extends JpaRepository<ProductionStageExtensionValue, Long> {

	List<ProductionStageExtensionValue> findByProductionStageExtensionIdInOrderByIdAsc(Collection<Long> ids);

}
