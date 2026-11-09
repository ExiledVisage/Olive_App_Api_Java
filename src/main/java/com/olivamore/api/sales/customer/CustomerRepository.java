package com.olivamore.api.sales.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

	List<Customer> findByActive(boolean active, Sort sort);

	Page<Customer> findByActive(boolean active, Pageable pageable);

	@Query(value = "select count(*) from orders where customer_id = :id", nativeQuery = true)
	long countOrders(long id);

}
