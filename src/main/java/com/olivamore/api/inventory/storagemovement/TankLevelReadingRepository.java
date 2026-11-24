package com.olivamore.api.inventory.storagemovement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface TankLevelReadingRepository extends JpaRepository<TankLevelReading, Long> {

	List<TankLevelReading> findByStorageUnitIdOrderByMeasuredAtDescIdDesc(long storageUnitId);

}
