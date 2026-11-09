package com.olivamore.api.productionline.stage;

import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code (production_line_id, stage_order)} unique oldugu icin siralar iki adimda kaydirilir:
 * once gecici olarak {@value #TEMP_OFFSET} ileri, sonra hedefe.
 */
@Service
public class ProductionStageService {

	private static final int TEMP_OFFSET = 100000;

	private final ProductionStageRepository repository;
	private final ProcessResources process;

	ProductionStageService(ProductionStageRepository repository, ProcessResources process) {
		this.repository = repository;
		this.process = process;
	}

	@Transactional(readOnly = true)
	List<ProductionStage> forLine(long lineId) {
		ensureLine(lineId);
		return repository.findByProductionLineIdOrderByStageOrderAsc(lineId);
	}

	@Transactional(readOnly = true)
	ProductionStage get(long lineId, long stageId) {
		return find(lineId, stageId);
	}

	/** Istenen sira 1..(son+1) araligina cekilir, o siradan sonrakiler bir ileri kayar. */
	@Transactional
	ProductionStage create(long lineId, CreateProductionStageRequest request) {
		ensureLine(lineId);
		assertProcessType(request.processTypeId());
		int last = repository.maxOrder(lineId);
		int order = Math.clamp(request.stageOrder(), 1, last + 1);
		shift(lineId, order, last, 1);
		return repository.save(new ProductionStage(lineId, order, request));
	}

	@Transactional
	ProductionStage update(long lineId, long stageId, UpdateProductionStageRequest request) {
		ProductionStage stage = find(lineId, stageId);
		Patch.ifPresent(request.processTypeId(), this::assertProcessType);
		if (request.stageOrder() != null) {
			reorder(stage, request.stageOrder());
		}
		stage.apply(request);
		return stage;
	}

	@Transactional
	void delete(long lineId, long stageId) {
		ProductionStage stage = find(lineId, stageId);
		String name = stage.getName() != null ? stage.getName() : "#" + stageId;
		DeletionGuard.of("'" + name + "' aşaması")
			.usedBy("parti prosesi", repository.countBatchProcesses(stageId))
			.check();
		int deleted = stage.getStageOrder();
		int last = repository.maxOrder(lineId);
		repository.delete(stage);
		repository.flush();
		shift(lineId, deleted + 1, last, -1);
	}

	/** Hat ve asama yoksa 404; asamanin bu hatta ait olmasi aranmaz. */
	@Transactional(readOnly = true)
	public void ensureExists(long lineId, long stageId) {
		ensureLine(lineId);
		if (!repository.existsById(stageId)) {
			throw new ModelNotFoundException("ProductionStage", stageId);
		}
	}

	private void reorder(ProductionStage stage, int requested) {
		long lineId = stage.getProductionLineId();
		int current = stage.getStageOrder();
		int last = repository.maxOrder(lineId);
		int order = Math.clamp(requested, 1, last);
		if (order == current) {
			return;
		}
		stage.moveTo(last + TEMP_OFFSET + stage.getId().intValue());
		repository.flush();
		if (order < current) {
			shift(lineId, order, current - 1, 1);
		}
		else {
			shift(lineId, current + 1, order, -1);
		}
		stage.moveTo(order);
	}

	private void shift(long lineId, int start, int end, int by) {
		if (start > end) {
			return;
		}
		LocalDateTime now = Timestamps.now();
		repository.moveOrders(lineId, start, end, TEMP_OFFSET, now);
		repository.moveOrders(lineId, start + TEMP_OFFSET, end + TEMP_OFFSET, by - TEMP_OFFSET, now);
	}

	private ProductionStage find(long lineId, long stageId) {
		ensureLine(lineId);
		ProductionStage stage = repository.findById(stageId)
			.orElseThrow(() -> new ModelNotFoundException("ProductionStage", stageId));
		if (stage.getProductionLineId() != lineId) {
			throw new HttpStatusException(404, "Stage not found for this production line.");
		}
		return stage;
	}

	private void ensureLine(long lineId) {
		if (!repository.lineExists(lineId)) {
			throw new ModelNotFoundException("ProductionLine", lineId);
		}
	}

	private void assertProcessType(Long processTypeId) {
		if (processTypeId != null && process.processTypesById(List.of(processTypeId)).isEmpty()) {
			throw LaravelValidationException.of("process_type_id",
				LaravelMessages.format(LaravelMessages.EXISTS, "process_type_id"));
		}
	}

}
