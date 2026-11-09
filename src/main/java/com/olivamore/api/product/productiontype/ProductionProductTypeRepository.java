package com.olivamore.api.product.productiontype;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ProductionProductTypeRepository extends JpaRepository<ProductionProductType, Long> {

	@Override
	@EntityGraph(attributePaths = "product")
	Optional<ProductionProductType> findById(Long id);

	@EntityGraph(attributePaths = "product")
	List<ProductionProductType> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = "product")
	List<ProductionProductType> findByProductIdOrderByIdAsc(long productId);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, long id);

}
