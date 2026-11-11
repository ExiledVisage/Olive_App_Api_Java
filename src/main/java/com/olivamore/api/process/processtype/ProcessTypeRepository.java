package com.olivamore.api.process.processtype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface ProcessTypeRepository extends JpaRepository<ProcessType, Long> {

	List<ProcessType> findAllByOrderByCreatedAtDescIdDesc();

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

	@Query(value = "select count(*) from production_stages where process_type_id = :id", nativeQuery = true)
	long countStages(long id);

	@Query(value = """
		select count(*) from batch_process_field_values
		where process_type_field_id in (select id from process_type_fields where process_type_id = :id)
		""", nativeQuery = true)
	long countMeasurements(long id);

}
