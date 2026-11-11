package com.olivamore.api.production.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;

@NoRepositoryBean
interface BatchProductTypeRepository<T extends BatchProductType> extends JpaRepository<T, Long> {

	List<T> findByProductionBatchIdInOrderBySortOrderAscIdAsc(Collection<Long> batchIds);

}
