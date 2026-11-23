package com.olivamore.api.machine;

import org.springframework.data.jpa.repository.JpaRepository;

interface MachinePartRepository extends JpaRepository<MachinePart, Long> {

	boolean existsByMachineIdAndCode(long machineId, String code);

	boolean existsByMachineIdAndCodeAndIdNot(long machineId, String code, long id);

	long countByMachineId(long machineId);

}
