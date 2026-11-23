package com.olivamore.api.machine;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

interface MachineRepository extends JpaRepository<Machine, Long>, JpaSpecificationExecutor<Machine> {

	@Override
	@EntityGraph(attributePaths = {"machineType", "parts"})
	Optional<Machine> findById(Long id);

	@Override
	@EntityGraph(attributePaths = {"machineType", "parts"})
	List<Machine> findAll(Specification<Machine> specification, Sort sort);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, long id);

}
