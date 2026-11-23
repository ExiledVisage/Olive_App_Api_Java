package com.olivamore.api.machine;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface MachineRunRepository extends JpaRepository<MachineRun, Long> {

	@EntityGraph(attributePaths = {"machineType", "machinePart.machine", "values.field"})
	List<MachineRun> findByBatchProcessIdOrderByStepOrder(long batchProcessId);

	Optional<MachineRun> findByBatchProcessIdAndStepOrder(long batchProcessId, int stepOrder);

	/** Parcalarin acik adimlari (parca ayni anda tek partide calisir). */
	List<MachineRun> findByMachinePartIdInAndFinishedAtIsNull(Collection<Long> partIds);

	void deleteByBatchProcessId(long batchProcessId);

	long countByMachinePartId(long partId);

	long countByMachinePartMachineId(long machineId);

}
