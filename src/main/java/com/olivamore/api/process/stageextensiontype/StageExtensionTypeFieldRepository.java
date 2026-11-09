package com.olivamore.api.process.stageextensiontype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface StageExtensionTypeFieldRepository extends JpaRepository<StageExtensionTypeField, Long> {

	List<StageExtensionTypeField> findByStageExtensionTypeIdInOrderBySortOrderAscIdAsc(Collection<Long> typeIds);

}
