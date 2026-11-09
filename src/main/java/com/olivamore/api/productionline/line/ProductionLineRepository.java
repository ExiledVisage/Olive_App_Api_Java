package com.olivamore.api.productionline.line;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface ProductionLineRepository extends JpaRepository<ProductionLine, Long> {

	List<ProductionLine> findAllByOrderByCreatedAtDesc();

	@Query("""
		select l from ProductionLine l
		where l.id in (select p.productionLineId from LineInputProduct p where p.productionProductId = :productId)
		order by l.id
		""")
	List<ProductionLine> withInputProduct(long productId);

	@Query("""
		select l from ProductionLine l
		where l.id in (select p.productionLineId from LineOutputProduct p where p.productionProductId = :productId)
		order by l.id
		""")
	List<ProductionLine> withOutputProduct(long productId);

	/** Urun ayni, tip bos ya da ayni. */
	@Query("""
		select l from ProductionLine l
		where l.id in (
			select p.productionLineId from LineInputProduct p
			where p.productionProductId = :productId
			and (p.productionProductTypeId is null or p.productionProductTypeId = :typeId))
		order by l.id
		""")
	List<ProductionLine> withInputType(long productId, long typeId);

	/** Urun ayni, tip bos ya da ayni. */
	@Query("""
		select l from ProductionLine l
		where l.id in (
			select p.productionLineId from LineOutputProduct p
			where p.productionProductId = :productId
			and (p.productionProductTypeId is null or p.productionProductTypeId = :typeId))
		order by l.id
		""")
	List<ProductionLine> withOutputType(long productId, long typeId);

	@Query(value = "select count(*) from production_batches where production_line_id = :id", nativeQuery = true)
	long countBatches(long id);

}
