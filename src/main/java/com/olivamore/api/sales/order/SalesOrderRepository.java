package com.olivamore.api.sales.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

	@Override
	@EntityGraph(attributePaths = {"customer", "deliveryAddress"})
	Optional<SalesOrder> findById(Long id);

	@Override
	@EntityGraph(attributePaths = {"customer", "deliveryAddress"})
	List<SalesOrder> findAll(Specification<SalesOrder> specification, Sort sort);

	@Override
	@EntityGraph(attributePaths = {"customer", "deliveryAddress"})
	Page<SalesOrder> findAll(Specification<SalesOrder> specification, Pageable pageable);

}
