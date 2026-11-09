package com.olivamore.api.finishedproduct.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RunMaterialRepository extends JpaRepository<RunMaterial, Long> {

	List<RunMaterial> findByRunIdOrderByIdAsc(long runId);

}
