package com.olivamore.api.process.stageextensiontype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StageExtensionTypeRepository extends JpaRepository<StageExtensionType, Long> {

	List<StageExtensionType> findAllByOrderByNameAsc();

}
