package com.olivamore.api.machine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface MachinePartRepository extends JpaRepository<MachinePart, Long> {

	boolean existsByMachineIdAndCode(long machineId, String code);

	boolean existsByMachineIdAndCodeAndIdNot(long machineId, String code, long id);

	long countByMachineId(long machineId);

	/** Tiplerin secilebilir parcalari: aktif makinenin aktif parcasi, makine koduna ve parca sirasina gore. */
	@Query("select p from MachinePart p join fetch p.machine m where m.machineType.id in :typeIds and m.active "
		+ "and p.active order by m.code, p.sortOrder, p.id")
	List<MachinePart> findSelectable(Collection<Long> typeIds);

}
