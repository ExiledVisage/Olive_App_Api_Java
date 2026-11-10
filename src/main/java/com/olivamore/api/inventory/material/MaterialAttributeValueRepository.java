package com.olivamore.api.inventory.material;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialAttributeValueRepository extends JpaRepository<MaterialAttributeValue, Long> {

	Optional<MaterialAttributeValue> findByMaterialIdAndDefinitionId(long materialId, long definitionId);

}
