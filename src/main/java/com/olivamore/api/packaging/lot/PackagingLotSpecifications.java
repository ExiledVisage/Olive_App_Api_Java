package com.olivamore.api.packaging.lot;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class PackagingLotSpecifications {

	private PackagingLotSpecifications() {
	}

	/** Bos verilen filtreler uygulanmaz; bilinmeyen durum sonuc dondurmez. */
	static Specification<PackagingLot> matching(Long itemId, Long supplierId, String status, String search) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (itemId != null) {
				predicates.add(cb.equal(root.get("item").get("id"), itemId));
			}
			if (supplierId != null) {
				predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
			}
			if (status != null && !status.isBlank()) {
				predicates.add(Arrays.stream(LotEnums.Status.values())
					.filter(candidate -> candidate.value().equals(status))
					.findFirst()
					.map(candidate -> cb.equal(root.get("status"), candidate))
					.orElseGet(cb::disjunction));
			}
			if (search != null && !search.isBlank()) {
				String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
				var item = root.join("item", JoinType.INNER);
				var supplier = root.join("supplier", JoinType.LEFT);
				predicates.add(cb.or(
					cb.like(cb.lower(root.get("lotCode")), like),
					cb.like(cb.lower(item.get("name")), like),
					cb.like(cb.lower(supplier.get("name")), like),
					cb.like(cb.lower(supplier.get("code")), like)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
