package com.olivamore.api.finishedproduct.product;

import com.olivamore.api.finishedproduct.recipe.FinishedProductRecipe;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FinishedProductRepository extends JpaRepository<FinishedProduct, Long> {

	@EntityGraph(attributePaths = "recipe")
	Optional<FinishedProduct> findWithRecipeById(long id);

	@EntityGraph(attributePaths = "recipe")
	List<FinishedProduct> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = "recipe")
	List<FinishedProduct> findByActiveOrderByCreatedAtDesc(boolean active);

	boolean existsByRecipeId(long recipeId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from FinishedProduct p where p.id = :id")
	Optional<FinishedProduct> findForUpdate(long id);

	/** Urunler tarifin yeni versiyonuna tasinir. */
	@Modifying
	@Query("update FinishedProduct p set p.recipe = :to, p.updatedAt = :at where p.recipe.id = :fromId")
	void moveToRecipe(long fromId, FinishedProductRecipe to, LocalDateTime at);

	/** Dolum tamamlaninca siseler fabrika stoguna eklenir. */
	@Modifying
	@Query("update FinishedProduct p set p.totalStock = p.totalStock + :quantity, p.updatedAt = :at where p.id = :id")
	void addStock(long id, int quantity, LocalDateTime at);

}
