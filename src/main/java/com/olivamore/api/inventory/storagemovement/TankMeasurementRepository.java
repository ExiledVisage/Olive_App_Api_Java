package com.olivamore.api.inventory.storagemovement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface TankMeasurementRepository extends JpaRepository<TankMeasurement, Long> {

	List<TankMeasurement> findByStorageUnitIdOrderByMeasuredAtDescIdDesc(long storageUnitId);

	List<TankMeasurement> findByStorageUnitIdAndMaterialIdOrderByMeasuredAtDescIdDesc(long storageUnitId,
		long materialId);

}
