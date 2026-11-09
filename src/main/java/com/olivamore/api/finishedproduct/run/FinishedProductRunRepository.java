package com.olivamore.api.finishedproduct.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinishedProductRunRepository extends JpaRepository<FinishedProductRun, Long> {

	@EntityGraph(attributePaths = {"product", "recipe"})
	Optional<FinishedProductRun> findWithDetailsById(long id);

	@EntityGraph(attributePaths = {"product", "recipe"})
	List<FinishedProductRun> findAllByOrderByCreatedAtDesc();

	@Override
	@EntityGraph(attributePaths = {"product", "recipe"})
	Page<FinishedProductRun> findAll(Pageable pageable);

	boolean existsByProductId(long productId);

	boolean existsByRecipeId(long recipeId);

}
