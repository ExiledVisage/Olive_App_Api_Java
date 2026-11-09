package com.olivamore.api.productionline.stageextension;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;

@NoRepositoryBean
interface ExtensionProductRepository<T extends ExtensionProduct> extends JpaRepository<T, Long> {

	List<T> findByProductionStageExtensionIdInOrderBySortOrderAscIdAsc(Collection<Long> extensionIds);

}
