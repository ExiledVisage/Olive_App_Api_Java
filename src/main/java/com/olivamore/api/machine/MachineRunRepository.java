package com.olivamore.api.machine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface MachineRunRepository extends JpaRepository<MachineRun, Long>, JpaSpecificationExecutor<MachineRun> {

	interface LastStart {

		Long getMachineId();

		LocalDateTime getStartedAt();

	}

	@EntityGraph(attributePaths = {"machineType", "machinePart.machine", "values.field"})
	List<MachineRun> findByBatchProcessIdOrderByStepOrder(long batchProcessId);

	Optional<MachineRun> findByBatchProcessIdAndStepOrder(long batchProcessId, int stepOrder);

	/** Parcalarin acik adimlari (parca ayni anda tek partide calisir). */
	List<MachineRun> findByMachinePartIdInAndFinishedAtIsNull(Collection<Long> partIds);

	void deleteByBatchProcessId(long batchProcessId);

	long countByMachinePartId(long partId);

	long countByMachinePartMachineId(long machineId);

	/** Makinelerin acik adimlari (su an calisan parcalar). */
	@Query("select r from MachineRun r join fetch r.machinePart p where p.machine.id in :machineIds "
		+ "and r.finishedAt is null order by r.startedAt")
	List<MachineRun> findOpenOfMachines(Collection<Long> machineIds);

	@Query("select p.machine.id as machineId, max(r.startedAt) as startedAt from MachineRun r join r.machinePart p "
		+ "where p.machine.id in :machineIds group by p.machine.id")
	List<LastStart> lastStarts(Collection<Long> machineIds);

	/** Makinenin donemle kesisen adimlari (sinirlar dahil; acik adim surer sayilir), baslangic sirasinda. */
	@EntityGraph(attributePaths = {"machinePart", "values.field"})
	@Query("select r from MachineRun r where r.machinePart.machine.id = :machineId and r.startedAt <= :to "
		+ "and (r.finishedAt is null or r.finishedAt > :from) order by r.startedAt, r.id")
	List<MachineRun> findInWindow(long machineId, LocalDateTime from, LocalDateTime to);

	/** Makinenin en son baslayan adimi. */
	@EntityGraph(attributePaths = "machinePart")
	Optional<MachineRun> findFirstByMachinePartMachineIdOrderByStartedAtDescIdDesc(long machineId);

	/** Sayfada olcumler satir basina yuklenir (koleksiyon fetch'i sayfalamayi bellege tasir). */
	@Override
	@EntityGraph(attributePaths = "machinePart")
	Page<MachineRun> findAll(Specification<MachineRun> specification, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = {"machinePart", "values.field"})
	List<MachineRun> findAll(Specification<MachineRun> specification, Sort sort);

}
