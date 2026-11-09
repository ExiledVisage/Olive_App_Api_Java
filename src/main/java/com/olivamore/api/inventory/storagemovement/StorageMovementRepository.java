package com.olivamore.api.inventory.storagemovement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface StorageMovementRepository
	extends JpaRepository<StorageMovement, Long>, JpaSpecificationExecutor<StorageMovement> {

	/** En yeni once. */
	List<StorageMovement> findByStorageUnitIdOrderByMovedAtDescIdDesc(long storageUnitId);

	Page<StorageMovement> findByStorageUnitIdOrderByMovedAtDescIdDesc(long storageUnitId, Pageable pageable);

	@Override
	List<StorageMovement> findAll(Specification<StorageMovement> specification, Sort sort);

	@Override
	Page<StorageMovement> findAll(Specification<StorageMovement> specification, Pageable pageable);

}
