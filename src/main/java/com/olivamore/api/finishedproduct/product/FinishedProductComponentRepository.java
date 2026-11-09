package com.olivamore.api.finishedproduct.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface FinishedProductComponentRepository extends JpaRepository<FinishedProductComponent, Long> {

	List<FinishedProductComponent> findByFinishedProductIdOrderByIdAsc(long productId);

	List<FinishedProductComponent> findByFinishedProductIdInOrderByIdAsc(Collection<Long> productIds);

	/** Hemen calisir: (urun, kalem) unique oldugu icin yeni satirlardan once silinmeli. */
	@Modifying
	@Query("delete from FinishedProductComponent c where c.finishedProductId = :productId")
	void deleteForProduct(long productId);

}
