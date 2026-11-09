package com.olivamore.api.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface SupplierFieldRepository extends JpaRepository<SupplierField, Long> {

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, long id);

	@Query("select f.code from SupplierField f where f.code in :codes")
	List<String> findExistingCodes(Collection<String> codes);

	@Query(value = "select count(*) from production_batches where supplier_field_id = :id", nativeQuery = true)
	long countBatches(long id);

}
