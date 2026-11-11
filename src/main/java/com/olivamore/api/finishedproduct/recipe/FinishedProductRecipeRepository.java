package com.olivamore.api.finishedproduct.recipe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FinishedProductRecipeRepository extends JpaRepository<FinishedProductRecipe, Long> {

	List<FinishedProductRecipe> findByActiveTrueOrderByCreatedAtDescIdDesc();

	@Query("""
		select r from FinishedProductRecipe r
		where lower(r.name) like lower(concat('%', :name, '%')) and r.active = true
		order by r.createdAt desc, r.id desc
		""")
	List<FinishedProductRecipe> search(String name);

	List<FinishedProductRecipe> findByNameOrderByVersionDesc(String name);

	@Query("select coalesce(max(r.version), 0) from FinishedProductRecipe r where r.name = :name")
	int maxVersion(String name);

	Optional<FinishedProductRecipe> findFirstByNameAndActiveTrue(String name);

}
