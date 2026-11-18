package com.olivamore.api.production.batch;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.inventory.MaterialDirectory;
import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.BatchProcess;
import com.olivamore.api.production.batchprocess.BatchProcessLifecycle;
import com.olivamore.api.production.batchprocess.BatchProcessRepository;
import com.olivamore.api.production.batchprocess.ProcessStatus;
import com.olivamore.api.productionline.ProductionLineResources.Stage;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Teslimati gruplara bolme (docs/BATCH_GROUPS.md). Grup teslimatin zeytininden bir pay alir ve hattin butun
 * asamalarini tasir: teslimatta yapilmis makine oncesi asamalar secilen baslangica kadar "teslimatta yapildi" diye
 * gecilir, gerisi beklemede acilir. Kismi ayirma yalniz bir tartimdan sonra; kalanin hepsi her an. Zeytinin hepsi
 * ayrilinca teslimatin bekleyen asamalari atlanir.
 */
@Service
class BatchGroupService {

	private static final BigDecimal EPSILON = new BigDecimal("0.00005");
	private static final Set<ProcessStatus> OPEN = Set.of(ProcessStatus.IN_PROGRESS, ProcessStatus.AWAITING_APPROVAL,
		ProcessStatus.RESTING, ProcessStatus.FAILED);

	private final ProductionBatchRepository batches;
	private final BatchInputTypeRepository inputs;
	private final BatchOutputTypeRepository outputs;
	private final BatchProcessRepository processes;
	private final BatchQueries queries;
	private final StockOperations stock;
	private final BatchProcessLifecycle lifecycle;

	BatchGroupService(ProductionBatchRepository batches, BatchInputTypeRepository inputs,
		BatchOutputTypeRepository outputs, BatchProcessRepository processes, BatchQueries queries, StockOperations stock,
		BatchProcessLifecycle lifecycle) {
		this.batches = batches;
		this.inputs = inputs;
		this.outputs = outputs;
		this.processes = processes;
		this.queries = queries;
		this.stock = stock;
		this.lifecycle = lifecycle;
	}

	/** @return yeni grubun id'si */
	@Transactional
	long split(long deliveryId, CreateBatchGroupRequest request, CurrentUser user) {
		ProductionBatch delivery = batches.lockById(deliveryId)
			.orElseThrow(() -> new ModelNotFoundException("ProductionBatch", deliveryId));
		boolean all = Boolean.TRUE.equals(request.allRemaining());
		ValidationErrors errors = new ValidationErrors();
		if (!all && request.quantityKg() == null) {
			errors.add("quantity_kg", MessageKeys.REQUIRED);
		}
		errors.throwIfAny();
		if (delivery.isGroup()) {
			throw new BusinessRuleException("'" + delivery.getBatchCode() + "' bir grup; grup yeniden bölünmez.");
		}
		StagePlan plan = StagePlan.of(queries.lineStages(delivery.getProductionLineId()));
		if (!plan.splittable()) {
			throw new BusinessRuleException("Partinin hattında makineli aşama yok; parti gruplara bölünmez.");
		}
		Map<Long, BatchProcess> own = processes.findByProductionBatchId(deliveryId).stream()
			.collect(Collectors.toMap(BatchProcess::getProductionStageId, Function.identity()));
		assertNothingRunning(plan, own);
		Stage start = startStage(request.startStageId(), plan, own);
		MaterialDirectory.Material olive = olive(deliveryId);
		BigDecimal portion = all ? olive.quantity() : request.quantityKg();
		if (!all) {
			assertMeasured(lastDone(plan, own));
			if (portion.subtract(olive.quantity()).compareTo(EPSILON) > 0) {
				throw new BusinessRuleException("Ayrılacak kilo (" + plain(portion) + " kg) ayrılmamış zeytinden ("
					+ plain(olive.quantity()) + " kg) fazla olamaz.");
			}
		}

		ProductionBatch group = batches.save(ProductionBatch.groupOf(delivery, nextCode(delivery), portion, user.id(),
			request.note()));
		long groupId = group.getId();
		copyTypes(deliveryId, groupId);
		openProcesses(groupId, plan, own, start);
		stock.allocateToBatch(olive.id(), groupId, portion);
		if (olive.quantity().subtract(portion).compareTo(EPSILON) <= 0) {
			LocalDateTime now = Timestamps.now();
			own.values().stream().filter(p -> p.getStatus() == ProcessStatus.WAITING).forEach(p -> p.skipUnneeded(now));
		}
		lifecycle.syncBatchStatus(groupId);
		return groupId;
	}

	private static void assertNothingRunning(StagePlan plan, Map<Long, BatchProcess> own) {
		List<String> running = plan.delivery().stream()
			.filter(s -> own.containsKey(s.id()) && OPEN.contains(own.get(s.id()).getStatus()))
			.map(StagePlan::label).toList();
		if (!running.isEmpty()) {
			throw new BusinessRuleException("Teslimatta süren aşama var: " + String.join(", ", running)
				+ ". Bölmek için aşamanın bitmesi gerekir.");
		}
	}

	/** Teslimatin son tamamlanan (ya da atlanan) asamasi; hic yoksa null. */
	private static Stage lastDone(StagePlan plan, Map<Long, BatchProcess> own) {
		Stage last = null;
		for (Stage stage : plan.delivery()) {
			if (isDone(own.get(stage.id()))) {
				last = stage;
			}
		}
		return last;
	}

	/** Kismi ayirma olculmus kilodan: son tamamlanan asama tartim olmali. */
	private void assertMeasured(Stage lastDone) {
		if (lastDone == null) {
			throw new BusinessRuleException("Teslimat henüz tartılmadı; önce kantarda tartılmalı.");
		}
		if (!queries.weighingTypes().contains(lastDone.processTypeId())) {
			throw new BusinessRuleException("Kısmi ayırma yalnız bir tartımdan sonra yapılır; son tamamlanan aşama '"
				+ StagePlan.label(lastDone) + "'. Önce tartın ya da kalanın hepsiyle grup açın.");
		}
	}

	/** Teslimatin partide duran tek aktif materyali (tartilmis zeytin). */
	private MaterialDirectory.Material olive(long deliveryId) {
		List<MaterialDirectory.Material> active = queries.activeMaterialsInBatch(deliveryId);
		if (active.isEmpty()) {
			throw new BusinessRuleException("Teslimatta ayrılacak zeytin yok; önce kantarda tartılmalı.");
		}
		if (active.size() > 1) {
			throw new BusinessRuleException("Teslimatta birden fazla materyal var; ayrılacak zeytin belirlenemedi.");
		}
		return active.getFirst();
	}

	/** Teslimatta yapilmis bir makine oncesi asama; verilmezse null (kaldigi yerden). */
	private static Stage startStage(Long stageId, StagePlan plan, Map<Long, BatchProcess> own) {
		if (stageId == null) {
			return null;
		}
		Optional<Stage> stage = plan.delivery().stream()
			.filter(s -> s.id().equals(stageId) && isDone(own.get(s.id())))
			.findFirst();
		if (stage.isEmpty()) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("start_stage_id", MessageKeys.IN);
			errors.throwIfAny();
		}
		return stage.get();
	}

	private String nextCode(ProductionBatch delivery) {
		long n = batches.countByParentBatchId(delivery.getId()) + 1;
		String code = delivery.getBatchCode() + "-" + n;
		while (batches.existsByBatchCode(code)) {
			n++;
			code = delivery.getBatchCode() + "-" + n;
		}
		return code;
	}

	private void copyTypes(long deliveryId, long groupId) {
		inputs.findByProductionBatchIdInOrderBySortOrderAscIdAsc(List.of(deliveryId)).forEach(t ->
			inputs.save(new BatchInputType(groupId, t.getProductionProductTypeId(), t.getSortOrder())));
		outputs.findByProductionBatchIdInOrderBySortOrderAscIdAsc(List.of(deliveryId)).forEach(t ->
			outputs.save(new BatchOutputType(groupId, t.getProductionProductTypeId(), t.getSortOrder())));
	}

	/** Teslimatta yapilmis ve baslangictan onceki asamalar gecilir; gerisi bekler. */
	private void openProcesses(long groupId, StagePlan plan, Map<Long, BatchProcess> own, Stage start) {
		LocalDateTime now = Timestamps.now();
		for (Stage stage : plan.delivery()) {
			BatchProcess process = new BatchProcess(groupId, stage.id());
			BatchProcess there = own.get(stage.id());
			if (isDone(there) && (start == null || stage.stageOrder() < start.stageOrder())) {
				process.inherit(there.getId(), now);
			}
			processes.save(process);
		}
		for (Stage stage : plan.group()) {
			processes.save(new BatchProcess(groupId, stage.id()));
		}
	}

	private static boolean isDone(BatchProcess process) {
		return process != null
			&& (process.getStatus() == ProcessStatus.COMPLETED || process.getStatus() == ProcessStatus.SKIPPED);
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

}
