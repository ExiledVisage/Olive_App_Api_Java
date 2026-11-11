package com.olivamore.api.production.batchprocess;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface BatchProcessFieldValueRepository extends JpaRepository<BatchProcessFieldValue, Long> {

	List<BatchProcessFieldValue> findByBatchProcessId(long processId);

	List<BatchProcessFieldValue> findByBatchProcessIdInOrderByIdAsc(Collection<Long> processIds);

	Optional<BatchProcessFieldValue> findByBatchProcessIdAndProcessTypeFieldId(long processId, long fieldId);

	@Modifying
	@Query("delete from BatchProcessFieldValue v where v.batchProcessId = :process and v.processTypeFieldId not in :fields")
	void deleteOthers(@Param("process") long processId, @Param("fields") Collection<Long> fieldIds);

}
