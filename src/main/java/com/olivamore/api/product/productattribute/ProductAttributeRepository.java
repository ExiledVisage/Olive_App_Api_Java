package com.olivamore.api.product.productattribute;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

	@Override
	@EntityGraph(attributePaths = "definition")
	Optional<ProductAttribute> findById(Long id);

	@EntityGraph(attributePaths = "definition")
	List<ProductAttribute> findByProductionProductIdOrderBySortOrderAscIdAsc(long productId);

	@EntityGraph(attributePaths = "definition")
	List<ProductAttribute> findByProductionProductIdOrderByIdAsc(long productId);

	boolean existsByProductionProductIdAndDefinitionAttributeKey(long productId, String attributeKey);

	boolean existsByProductionProductIdAndDefinitionAttributeKeyAndIdNot(long productId, String attributeKey, long id);

}
