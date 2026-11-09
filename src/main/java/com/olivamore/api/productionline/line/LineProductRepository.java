package com.olivamore.api.productionline.line;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;

@NoRepositoryBean
interface LineProductRepository<T extends LineProduct> extends JpaRepository<T, Long> {

	List<T> findByProductionLineIdInOrderBySortOrderAscIdAsc(Collection<Long> lineIds);

	/** Hemen calisir: (hat, urun) unique oldugu icin yeni satirlardan once silinmeli. */
	@Modifying
	@Query("delete from #{#entityName} p where p.productionLineId = :lineId")
	void deleteForLine(long lineId);

}
