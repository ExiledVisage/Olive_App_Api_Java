package com.olivamore.api.supplier;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SupplierSpecifications {

	private SupplierSpecifications() {
	}

	/**
	 * @param isActive {@code null} ise filtre yok
	 * @param search   ad, kod ya da tarla kodunda buyuk/kucuk harfe duyarsiz; bos ise filtre yok
	 */
	static Specification<Supplier> matching(Boolean isActive, String search) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (isActive != null) {
				predicates.add(cb.equal(root.get("active"), isActive));
			}
			if (search != null && !search.isBlank()) {
				String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
				Subquery<Long> fields = query.subquery(Long.class);
				var field = fields.from(SupplierField.class);
				fields.select(field.get("id")).where(cb.equal(field.get("supplier"), root),
					cb.like(cb.lower(field.get("code")), like));
				predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
					cb.like(cb.lower(root.get("code")), like), cb.exists(fields)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
