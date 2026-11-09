package com.olivamore.api.packaging.supplier;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductSupplierRepository
	extends JpaRepository<ProductSupplier, Long>, JpaSpecificationExecutor<ProductSupplier> {

	interface LotCount {

		Long getSupplierId();

		long getTotal();

	}

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, long id);

	@Query(value = """
		select product_supplier_id as supplierId, count(*) as total from packaging_lots
		where product_supplier_id is not null group by product_supplier_id
		""", nativeQuery = true)
	List<LotCount> countLots();

	@Query(value = "select count(*) from packaging_lots where product_supplier_id = :id", nativeQuery = true)
	long countLots(long id);

	/** Ad veya kod, buyuk/kucuk harfe duyarsiz. */
	static Specification<ProductSupplier> matching(Boolean isActive, String search) {
		return (root, query, cb) -> {
			var predicate = cb.conjunction();
			if (isActive != null) {
				predicate = cb.and(predicate, cb.equal(root.get("active"), isActive));
			}
			if (search != null && !search.isBlank()) {
				String like = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
				predicate = cb.and(predicate, cb.or(cb.like(cb.lower(root.get("name")), like),
					cb.like(cb.lower(root.get("code")), like)));
			}
			return predicate;
		};
	}

}
