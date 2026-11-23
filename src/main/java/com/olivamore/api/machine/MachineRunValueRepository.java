package com.olivamore.api.machine;

import org.springframework.data.jpa.repository.JpaRepository;

interface MachineRunValueRepository extends JpaRepository<MachineRunValue, Long> {

	long countByFieldId(long fieldId);

}
