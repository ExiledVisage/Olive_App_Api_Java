package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.PackagingRunDirectory;
import com.olivamore.api.packaging.PackagingRunDirectory.Run;
import com.olivamore.api.packaging.lot.PackagingLotResponse.Movement;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Entity'den API cevabina. Dolum kosusu bilgileri {@link PackagingRunDirectory} uzerinden gelir. */
@Component
public class PackagingLotResponses {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id"));

	private final PackagingLotRepository lots;
	private final PackagingLotMovementRepository movements;
	private final RunPackagingRepository packagings;
	private final PackagingRunDirectory runDirectory;

	PackagingLotResponses(PackagingLotRepository lots, PackagingLotMovementRepository movements,
		RunPackagingRepository packagings, PackagingRunDirectory runDirectory) {
		this.lots = lots;
		this.movements = movements;
		this.packagings = packagings;
		this.runDirectory = runDirectory;
	}

	/** Giris zamani ve id'ye gore yeniden eskiye. */
	@Transactional(readOnly = true)
	List<PackagingLotResponse> list(Long itemId, Long supplierId, String status, String search) {
		return lots.findAll(PackagingLotSpecifications.matching(itemId, supplierId, status, search), NEWEST_FIRST)
			.stream().map(lot -> PackagingLotResponse.of(lot, null, null)).toList();
	}

	@Transactional(readOnly = true)
	public List<PackagingLotResponse> ofSupplier(long supplierId) {
		return list(null, supplierId, null, null);
	}

	/** Hareketler ve bu lottan ceken dolum kosulari. */
	@Transactional(readOnly = true)
	PackagingLotResponse detail(long lotId) {
		PackagingLot lot = lots.findById(lotId).orElseThrow(() -> new ModelNotFoundException("PackagingLot", lotId));
		List<PackagingLotMovement> movementRows = movements.findByPackagingLotIdInOrderByMovedAtAscIdAsc(List.of(lotId));
		List<RunPackaging> packagingRows = packagings.findByPackagingLotIdInOrderByIdAsc(List.of(lotId));
		List<Long> runIds = new ArrayList<>(movementRows.stream().map(PackagingLotMovement::getFinishedProductRunId).toList());
		runIds.addAll(packagingRows.stream().map(RunPackaging::getFinishedProductRunId).toList());
		Map<Long, Run> runs = runDirectory.runs(runIds);
		return PackagingLotResponse.of(lot,
			movementRows.stream().map(row -> movement(row, runs)).toList(),
			packagingRows.stream().map(row -> run(row, runs)).toList());
	}

	private static Movement movement(PackagingLotMovement row, Map<Long, Run> runs) {
		Long runId = row.getFinishedProductRunId();
		Run run = runId == null ? null : runs.get(runId);
		return new Movement(row.getId(), row.getMovementType().value(), row.getSourceType().value(), row.getQuantity(),
			row.getMovementType().incoming(), row.getNote(), row.getMovedAt(), runId,
			run == null ? null : run.productName(), row.getCreatedAt());
	}

	private static PackagingLotResponse.Run run(RunPackaging row, Map<Long, Run> runs) {
		Run run = runs.get(row.getFinishedProductRunId());
		return new PackagingLotResponse.Run(run.id(), run.status(), row.getQuantity(), run.productName(), run.quantity(), run.completedAt());
	}

}
