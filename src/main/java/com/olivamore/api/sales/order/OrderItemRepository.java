package com.olivamore.api.sales.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	List<OrderItem> findByOrderIdOrderByIdAsc(long orderId);

	List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

	/** Hemen calisir: (siparis, urun) unique oldugu icin yeni kalemlerden once silinmeli. */
	@Modifying
	@Query("delete from OrderItem i where i.orderId = :orderId")
	void deleteForOrder(long orderId);

}
