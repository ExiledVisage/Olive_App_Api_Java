package com.olivamore.api.process.processtypefield;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface ProcessTypeFieldRepository extends JpaRepository<ProcessTypeField, Long> {

	List<ProcessTypeField> findByProcessTypeIdOrderBySortOrderAscIdAsc(long processTypeId);

	List<ProcessTypeField> findByProcessTypeIdInOrderBySortOrderAscIdAsc(Collection<Long> processTypeIds);

	boolean existsByProcessTypeIdAndFieldKey(long processTypeId, String fieldKey);

	boolean existsByProcessTypeIdAndFieldKeyAndIdNot(long processTypeId, String fieldKey, long id);

	@Query(value = "select count(*) from batch_process_field_values where process_type_field_id = :id",
		nativeQuery = true)
	long countMeasurements(long id);

}
