package com.olivamore.api.packaging.lot;

import com.olivamore.api.packaging.lot.PackagingLotResponse.Movement;
import com.olivamore.api.packaging.lot.PackagingLotResponse.Run;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entity'den API cevabina. Dolum kosusu bilgileri bitmis urun tablolarindan salt okunur. */
@Component
public class PackagingLotResponses {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id"));

	private record RunRow(Long id, String status, Integer quantity, LocalDateTime completedAt, String productName) {
	}

	private final PackagingLotRepository lots;
	private final PackagingLotMovementRepository movements;
	private final RunPackagingRepository packagings;
	private final JdbcClient jdbc;

	PackagingLotResponses(PackagingLotRepository lots, PackagingLotMovementRepository movements,
		RunPackagingRepository packagings, JdbcClient jdbc) {
		this.lots = lots;
		this.movements = movements;
		this.packagings = packagings;
		this.jdbc = jdbc;
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
		Map<Long, RunRow> runs = runs(runIds);
		return PackagingLotResponse.of(lot,
			movementRows.stream().map(row -> movement(row, runs)).toList(),
			packagingRows.stream().map(row -> run(row, runs)).toList());
	}

	private Map<Long, RunRow> runs(List<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select r.id, r.status, r.quantity, r.completed_at, p.name as product_name
			from finished_product_runs r
			left join finished_products p on p.id = r.finished_product_id
			where r.id in (:ids)
			""").param("ids", present).query(RunRow.class).list().stream()
			.collect(Collectors.toMap(RunRow::id, Function.identity()));
	}

	private static Movement movement(PackagingLotMovement row, Map<Long, RunRow> runs) {
		Long runId = row.getFinishedProductRunId();
		RunRow run = runId == null ? null : runs.get(runId);
		return new Movement(row.getId(), row.getMovementType().value(), row.getSourceType().value(), row.getQuantity(),
			row.getMovementType().incoming(), row.getNote(), row.getMovedAt(), runId,
			run == null ? null : run.productName(), row.getCreatedAt());
	}

	private static Run run(RunPackaging row, Map<Long, RunRow> runs) {
		RunRow run = runs.get(row.getFinishedProductRunId());
		return new Run(run.id(), run.status(), row.getQuantity(), run.productName(), run.quantity(), run.completedAt());
	}

}
