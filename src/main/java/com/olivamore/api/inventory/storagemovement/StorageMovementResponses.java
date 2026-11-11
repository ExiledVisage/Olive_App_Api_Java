package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialQueries;
import com.olivamore.api.inventory.material.MaterialResponses;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.olivamore.api.inventory.material.MaterialResponses.lookup;

/** Hareket cevaplari: materyal (tip + attribute degerleri), proses, karsi tank; tank yalnizca yazmada. */
@Component
public class StorageMovementResponses {

	private final MaterialQueries queries;
	private final MaterialResponses materials;

	StorageMovementResponses(MaterialQueries queries, MaterialResponses materials) {
		this.queries = queries;
		this.materials = materials;
	}

	/** Tank listesi: tankin kendisi yuklu degil. */
	public List<StorageMovementResponse> list(List<StorageMovement> movements) {
		return movements(movements, false);
	}

	/** Yazma cevaplari. */
	public List<StorageMovementResponse> written(List<StorageMovement> movements) {
		return movements(movements, true);
	}

	/** Bosaltmada acilan tortu materyalleri. */
	public List<MaterialResponse> residues(List<Long> materialIds) {
		return materials.materials(materialIds, false);
	}

	private List<StorageMovementResponse> movements(List<StorageMovement> movements, boolean withStorageUnit) {
		Map<Long, MaterialResponse> materialResponses = materials.materials(
			movements.stream().map(StorageMovement::getMaterialId).distinct().toList(), false).stream()
			.collect(Collectors.toMap(MaterialResponse::id, Function.identity()));
		Map<Long, MaterialQueries.BatchProcess> processes = queries.batchProcesses(
			movements.stream().map(StorageMovement::getBatchProcessId).toList());
		Map<Long, MaterialQueries.Unit> units = queries.units(movements.stream()
			.flatMap(m -> Stream.of(m.getStorageUnitId(), m.getCounterpartyStorageUnitId())).toList());
		return movements.stream().map(m -> {
			MaterialQueries.BatchProcess process = lookup(processes, m.getBatchProcessId());
			return new StorageMovementResponse(m.getId(), m.getStorageUnitId(), m.getMaterialId(),
				m.getSourceMaterialId(), m.getBatchProcessId(), m.getFinishedProductRunId(),
				m.getCounterpartyStorageUnitId(), m.getSourceType(), m.getMovementType(), m.getTransferReference(),
				m.getQuantity(), m.getUnit(), m.getNote(), m.getMovedAt(),
				withStorageUnit ? Optional.ofNullable(summary(lookup(units, m.getStorageUnitId()))) : null,
				lookup(materialResponses, m.getMaterialId()), process == null ? null : MaterialResponses.process(process),
				summary(lookup(units, m.getCounterpartyStorageUnitId())), m.getCreatedAt(), m.getUpdatedAt());
		}).toList();
	}

	private static MaterialResponse.UnitSummary summary(MaterialQueries.Unit unit) {
		return unit == null ? null : new MaterialResponse.UnitSummary(unit.id(), unit.name(), unit.code());
	}

}
