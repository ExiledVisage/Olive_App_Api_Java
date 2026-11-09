package com.olivamore.api.product.productionproduct;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface ProductionProductRepository extends JpaRepository<ProductionProduct, Long> {

	interface TypeCount {

		Long getProductId();

		long getTotal();

	}

	List<ProductionProduct> findAllByOrderByCreatedAtDesc();

	boolean existsByKey(String key);

	boolean existsByName(String name);

	boolean existsByKeyAndIdNot(String key, long id);

	boolean existsByNameAndIdNot(String name, long id);

	@Query("""
		select t.product.id as productId, count(t) as total
		from ProductionProductType t group by t.product.id
		""")
	List<TypeCount> countTypes();

	@Query("select count(t) from ProductionProductType t where t.product.id = :productId")
	long countTypes(long productId);

}
