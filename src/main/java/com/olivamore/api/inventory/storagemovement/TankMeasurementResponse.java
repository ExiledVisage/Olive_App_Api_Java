package com.olivamore.api.inventory.storagemovement;

import java.time.LocalDateTime;

/** Son tank olcumu: hesaplanan, olculen ve fark (olcum oncesi deger yoksa hesaplanan ve fark null). */
record TankMeasurementResponse(
	Long id,
	Long storageUnitId,
	Long materialId,
	Long productionProductAttributeDefinitionId,
	String label,
	Number calculatedValue,
	Number measuredValue,
	Number difference,
	MeasuredBy measuredBy,
	String note,
	LocalDateTime measuredAt) {

	record MeasuredBy(Long id, String name) {
	}

}
