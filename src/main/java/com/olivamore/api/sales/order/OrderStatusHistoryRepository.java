package com.olivamore.api.sales.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

	List<OrderStatusHistory> findByOrderIdOrderByChangedAtAscIdAsc(long orderId);

}
