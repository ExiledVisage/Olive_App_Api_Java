package com.olivamore.api.production.batchprocess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface BatchProcessReviewRepository extends JpaRepository<BatchProcessReview, Long> {

	List<BatchProcessReview> findByBatchProcessIdInOrderByReviewedAtAscIdAsc(Collection<Long> processIds);

}
