package com.olivamore.api.sales.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CustomerPreferredRecipeRepository extends JpaRepository<CustomerPreferredRecipe, Long> {

	Optional<CustomerPreferredRecipe> findByCustomerIdAndFinishedProductRecipeId(long customerId, long recipeId);

}
