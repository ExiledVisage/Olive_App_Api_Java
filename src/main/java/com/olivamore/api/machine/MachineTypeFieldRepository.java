package com.olivamore.api.machine;

import org.springframework.data.jpa.repository.JpaRepository;

interface MachineTypeFieldRepository extends JpaRepository<MachineTypeField, Long> {

	boolean existsByMachineTypeIdAndFieldKey(long machineTypeId, String fieldKey);

}
