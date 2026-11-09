package com.olivamore.api.supplier;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

	interface BatchCount {

		Long getSupplierId();

		long getTotal();

	}

	@Override
	@EntityGraph(attributePaths = "fields")
	Optional<Supplier> findById(Long id);

	@Override
	@EntityGraph(attributePaths = "fields")
	List<Supplier> findAll(Specification<Supplier> specification, Sort sort);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, long id);

	@Query(value = """
		select supplier_id as supplierId, count(*) as total from production_batches
		where supplier_id is not null group by supplier_id
		""", nativeQuery = true)
	List<BatchCount> countBatches();

	@Query(value = "select count(*) from production_batches where supplier_id = :id", nativeQuery = true)
	long countBatches(long id);

}
