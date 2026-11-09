package com.olivamore.api.packaging.lot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface RunPackagingRepository extends JpaRepository<RunPackaging, Long> {

	List<RunPackaging> findByFinishedProductRunIdOrderByIdAsc(long runId);

	List<RunPackaging> findByPackagingLotIdInOrderByIdAsc(Collection<Long> lotIds);

	boolean existsByFinishedProductRunId(long runId);

	boolean existsByPackagingLotId(long lotId);

	/** Hemen calisir: (kosu, lot) unique oldugu icin yeni satirlardan once silinmeli. */
	@Modifying
	@Query("delete from RunPackaging p where p.finishedProductRunId = :runId")
	void deleteForRun(long runId);

}
