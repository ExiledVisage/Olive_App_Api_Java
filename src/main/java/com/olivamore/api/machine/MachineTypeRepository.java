package com.olivamore.api.machine;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface MachineTypeRepository extends JpaRepository<MachineType, Long> {

	interface MachineCount {

		Long getMachineTypeId();

		long getTotal();

	}

	@Override
	@EntityGraph(attributePaths = "fields")
	Optional<MachineType> findById(Long id);

	@Override
	@EntityGraph(attributePaths = "fields")
	List<MachineType> findAll(Sort sort);

	boolean existsByKey(String key);

	@Query(value = "select machine_type_id as machineTypeId, count(*) as total from machines group by machine_type_id",
		nativeQuery = true)
	List<MachineCount> countMachines();

	@Query(value = "select count(*) from machines where machine_type_id = :id", nativeQuery = true)
	long countMachines(long id);

}
