package com.olivamore.api.product.productattribute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {

	List<AttributeDefinition> findAllByOrderByLabelAsc();

	Optional<AttributeDefinition> findByAttributeKey(String attributeKey);

}
