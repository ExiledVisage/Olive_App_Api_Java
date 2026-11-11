package com.olivamore.api.production.batchprocess;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Permissions;
import com.olivamore.api.inventory.PreSettlingTankEmptied;
import com.olivamore.api.production.batch.ProductionBatch;
import com.olivamore.api.production.batch.ProductionBatchRepository;
import com.olivamore.api.production.batchprocess.BatchProcessFieldValue.Value;
import com.olivamore.api.production.batchprocess.stage.OperationData;
import com.olivamore.api.production.batchprocess.stage.OperationDataRules;
import com.olivamore.api.production.batchprocess.stage.StageExtensions;
import com.olivamore.api.production.batchprocess.stage.StageProcess;
import com.olivamore.api.production.batchprocess.stage.StageQueries;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Field;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Stage;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.IsoDateTimeDeserializer;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asama durumu (kati sira, geri alma, atlama, basarisizlik), tamamlama (uzanti isleyicileri), onay akisi (gonderim,
 * onay, red) ve partinin durumunun proseslerden turetilmesi.
 * <p>
 * Onay gerektiren asamada tamamlama "onay bekliyor"a gecer: isleyiciler gonderim aninda bir savepoint icinde denenip
 * geri alinir (kural hatasi isciye hemen doner), etki onayda islenir.
 * <p>
 * On dinlendirme asamasi tamamlanmaz, "dinlenmede" bekler: tank bosaltilinca ({@link PreSettlingTankEmptied}) ayni
 * islemde tamamlanir; o zamana kadar yalnizca iptal edilebilir (waiting), urun partiye doner.
 */
@Service
class BatchProcessService {

	private static final String COMPLETE_PERMISSION = "batch_process.complete";
	private static final String SKIP_PERMISSION = "batch_process.skip";
	private static final String REOPEN_PERMISSION = "batch_process.reopen";

	private final BatchProcessRepository processes;
	private final BatchProcessFieldValueRepository fieldValues;
	private final BatchProcessReviewRepository reviews;
	private final ProductionBatchRepository batches;
	private final StageQueries queries;
	private final StageExtensions handlers;
	private final OperationDataRules operationRules;
	private final Permissions permissions;
	private final DataSource dataSource;

	@PersistenceContext
	private EntityManager entityManager;

	BatchProcessService(BatchProcessRepository processes, BatchProcessFieldValueRepository fieldValues,
		BatchProcessReviewRepository reviews, ProductionBatchRepository batches, StageQueries queries,
		StageExtensions handlers, OperationDataRules operationRules, Permissions permissions, DataSource dataSource) {
		this.processes = processes;
		this.fieldValues = fieldValues;
		this.reviews = reviews;
		this.batches = batches;
		this.queries = queries;
		this.handlers = handlers;
		this.operationRules = operationRules;
		this.permissions = permissions;
		this.dataSource = dataSource;
	}

	// ======================================================================
	// Guncelleme ve durum
	// ======================================================================

	/** Gonderilen alanlar; {@code status} varsa durum gecisi (kaydetme dahil), yoksa yalnizca kaydetme. */
	@Transactional
	void update(long id, UpdateBatchProcessRequest request, CurrentUser actor) {
		BatchProcess process = find(id);
		ProcessStatus status = request.status();
		authorize(process, status, actor);
		JsonNode sent = OperationData.normalized(request.operationData());
		ValidationErrors errors = new ValidationErrors();
		if (status == ProcessStatus.AWAITING_APPROVAL || status == ProcessStatus.RESTING) {
			errors.add("status", MessageKeys.IN);
		}
		checkOperator(errors, request.operatorId());
		Patch<LocalDateTime> startedAt = date(errors, "started_at", request.startedAt());
		Patch<LocalDateTime> completedAt = date(errors, "completed_at", request.completedAt());
		JsonNode data = checkOperationData(errors, process, status, sent);
		errors.throwIfAny();

		// Zaten tamamlanmissa bu istekteki alanlar da kaydedilmez
		if (!(status == ProcessStatus.COMPLETED && process.getStatus() == ProcessStatus.COMPLETED)) {
			Patch.ifPresent(request.note(), process::changeNote);
			Patch.ifPresent(request.operatorId(), process::changeOperator);
			Patch.ifPresent(startedAt, process::changeStartedAt);
			Patch.ifPresent(completedAt, process::changeCompletedAt);
		}
		long batchId = process.getProductionBatchId();
		if (status != null) {
			applyStatus(process, status, data, actor);
		}
		syncBatchStatus(batchId);
	}

	/** Tamamlama, atlama ve kapanmis asamayi geri acma ayri yetkiler; kurallardan once (403). */
	private void authorize(BatchProcess process, ProcessStatus status, CurrentUser user) {
		if (status == null) {
			return;
		}
		String required = switch (status) {
			case COMPLETED -> COMPLETE_PERMISSION;
			case SKIPPED -> SKIP_PERMISSION;
			case WAITING -> isOpen(process) ? null : REOPEN_PERMISSION;
			default -> null;
		};
		if (required != null && !permissions.can(user, required)) {
			throw HttpStatusException.forbidden("This action is unauthorized.");
		}
	}

	private static boolean isOpen(BatchProcess process) {
		return process.getStatus() == ProcessStatus.WAITING || process.getStatus() == ProcessStatus.IN_PROGRESS;
	}

	private void checkOperator(ValidationErrors errors, Patch<Long> operatorId) {
		if (operatorId != null && operatorId.value() != null && !queries.userExists(operatorId.value())) {
			errors.add("operator_id", MessageKeys.EXISTS);
		}
	}

	/** Yalnizca asama tamamlanirken (zaten tamamlanmamis/onay beklemiyorsa) uzantilara gore. */
	private JsonNode checkOperationData(ValidationErrors errors, BatchProcess process, ProcessStatus status,
		JsonNode data) {
		if (status == ProcessStatus.COMPLETED && process.getStatus() != ProcessStatus.COMPLETED
			&& process.getStatus() != ProcessStatus.AWAITING_APPROVAL && process.getStatus() != ProcessStatus.RESTING) {
			List<Extension> extensions = extensions(process);
			operationRules.check(errors, extensions, data);
			return operationRules.declaredOnly(extensions, data);
		}
		operationRules.checkOptional(errors, data);
		return data;
	}

	private List<Extension> extensions(BatchProcess process) {
		return queries.extensions(process.getProductionStageId());
	}

	private void applyStatus(BatchProcess p, ProcessStatus status, JsonNode data, CurrentUser actor) {
		if (p.getStatus() == ProcessStatus.AWAITING_APPROVAL) {
			throw new BusinessRuleException(
				"'" + stageLabel(p) + "' aşaması onay bekliyor; yetkili onaylamalı ya da reddetmeli.");
		}
		if (p.getStatus() == ProcessStatus.RESTING) {
			cancelResting(p, status);
			return;
		}
		if (status != ProcessStatus.WAITING) {
			assertPreviousStagesDone(p);
		}
		LocalDateTime now = Timestamps.now();
		switch (status) {
			case WAITING -> {
				assertNoLaterStageStarted(p);
				assertNoSideEffectsToUndo(p, "waiting");
				p.reopen();
			}
			case IN_PROGRESS -> {
				if (p.getStatus() == ProcessStatus.COMPLETED) {
					throw new BusinessRuleException("Tamamlanmış bir batch process tekrar in_progress yapılamaz.");
				}
				p.begin(now);
			}
			case COMPLETED -> {
				if (p.getStatus() != ProcessStatus.COMPLETED && requiresApproval(p)) {
					submitForApproval(p, data, actor, now);
				}
				else {
					complete(p, data, now);
				}
			}
			case SKIPPED -> {
				assertNoSideEffectsToUndo(p, "skipped");
				if (stage(p).isRequired()) {
					throw new BusinessRuleException("'" + stageLabel(p) + "' zorunlu bir aşama; atlanamaz.");
				}
				p.skip(now);
			}
			case FAILED -> p.fail(now);
			default -> {
			}
		}
	}

	/** Zaten tamamlanmissa hicbir sey yapilmaz. */
	private void complete(BatchProcess p, JsonNode data, LocalDateTime now) {
		if (p.getStatus() == ProcessStatus.COMPLETED) {
			return;
		}
		p.markStarted(now);
		assertRequiredFieldsFilled(p);
		List<Extension> extensions = extensions(p);
		handlers.dispatch(context(p), extensions, new OperationData(data));
		if (StageExtensions.rests(extensions)) {
			p.rest(OperationData.forStorage(data));
		}
		else {
			p.finish(OperationData.forStorage(data), now);
		}
	}

	/** Dinlenmedeki asama yalnizca iptal edilir: tanktaki urun partiye doner, asama bekleyene doner. */
	private void cancelResting(BatchProcess p, ProcessStatus status) {
		if (status != ProcessStatus.WAITING) {
			throw new BusinessRuleException("'" + stageLabel(p) + "' aşaması dinlenmede; ön dinlendirme tankı "
				+ "boşaltılınca tamamlanır. Yanlış aktarımsa aşama iptal edilir (waiting).");
		}
		assertNoLaterStageStarted(p);
		handlers.cancelPreSettling(p.getId());
		p.reopen();
	}

	// ======================================================================
	// On dinlendirme tankinin bosaltilmasi
	// ======================================================================

	/**
	 * Tank bosaltildi (ayni islemde): her girdinin tortusu acilir, asama tamamlanir ve tortu (kg) asamanin verisine
	 * {@code residue_kg} olarak yazilir.
	 */
	@EventListener
	void completeResting(PreSettlingTankEmptied event) {
		Map<Long, BigDecimal> residues = new LinkedHashMap<>();
		for (PreSettlingTankEmptied.Entry entry : event.entries()) {
			BatchProcess process = find(entry.batchProcessId());
			if (process.getStatus() != ProcessStatus.RESTING) {
				throw new IllegalStateException("Proses " + process.getId() + " dinlenmede degil: "
					+ process.getStatus().value());
			}
			handlers.residue(context(process), extensions(process), entry.materialId(), entry.residueKg());
			residues.merge(process.getId(), entry.residueKg(), BigDecimal::add);
		}
		LocalDateTime now = Timestamps.now();
		Set<Long> batchIds = new LinkedHashSet<>();
		residues.forEach((id, residueKg) -> {
			BatchProcess process = find(id);
			ObjectNode data = process.getOperationData() instanceof ObjectNode object ? object.deepCopy()
				: JsonNodeFactory.instance.objectNode();
			data.put("residue_kg", residueKg);
			process.finish(data, now);
			batchIds.add(process.getProductionBatchId());
		});
		batchIds.forEach(this::syncBatchStatus);
	}

	// ======================================================================
	// Onay akisi
	// ======================================================================

	/** Asamada isaretliyse, depo hareketi yapiyorsa ya da hattin son asamasiysa. */
	boolean requiresApproval(BatchProcess process) {
		Stage stage = stage(process);
		if (stage.requiresApproval()) {
			return true;
		}
		return StageExtensions.movesStock(queries.extensions(stage.id()))
			|| stage.stageOrder() == queries.lastStageOrder(stage.productionLineId());
	}

	private void submitForApproval(BatchProcess p, JsonNode data, CurrentUser actor, LocalDateTime now) {
		p.markStarted(now);
		assertRequiredFieldsFilled(p);
		dryRun(p, data).submit(OperationData.forStorage(data), actor.id(), now);
	}

	/**
	 * Kurallar gonderim aninda denenip geri alinir (istegin transaction'inda bir savepoint). Hata isciye hemen doner.
	 * Geri alinan satirlar persistence context'te kalmasin diye context temizlenir.
	 * @return yeniden yuklenen proses
	 */
	private BatchProcess dryRun(BatchProcess process, JsonNode data) {
		StageProcess context = context(process);
		long id = process.getId();
		entityManager.flush();
		Connection connection = DataSourceUtils.getConnection(dataSource);
		Savepoint savepoint;
		try {
			savepoint = connection.setSavepoint();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Savepoint acilamadi", e);
		}
		try {
			handlers.dispatch(context, extensions(process), new OperationData(data));
		}
		finally {
			entityManager.clear();
			try {
				connection.rollback(savepoint);
			}
			catch (SQLException e) {
				throw new IllegalStateException("Savepoint'e geri donulemedi", e);
			}
		}
		return find(id);
	}

	@Transactional
	void approve(long id, CurrentUser reviewer, String note) {
		BatchProcess process = find(id);
		assertAwaitingApproval(process);
		assertPreviousStagesDone(process);
		List<Extension> extensions = extensions(process);
		handlers.dispatch(context(process), extensions, new OperationData(process.getOperationData()));
		recordReview(process, "approved", reviewer, note);
		process.approve(reviewer.id(), Timestamps.now());
		if (StageExtensions.rests(extensions)) {
			process.rest(process.getOperationData());
		}
		syncBatchStatus(process.getProductionBatchId());
	}

	@Transactional
	void reject(long id, CurrentUser reviewer, String reason) {
		BatchProcess process = find(id);
		assertAwaitingApproval(process);
		recordReview(process, "rejected", reviewer, reason);
		process.reject();
		syncBatchStatus(process.getProductionBatchId());
	}

	private void recordReview(BatchProcess process, String decision, CurrentUser reviewer, String note) {
		reviews.save(new BatchProcessReview(process.getId(), decision, reviewer.id(), process.getSubmittedByUserId(),
			process.getOperationData(), note, Timestamps.now()));
	}

	private void assertAwaitingApproval(BatchProcess process) {
		if (process.getStatus() != ProcessStatus.AWAITING_APPROVAL) {
			throw new BusinessRuleException("'" + stageLabel(process) + "' aşaması onay beklemiyor (durum: "
				+ process.getStatus().value() + ").");
		}
	}

	// ======================================================================
	// Alan degerleri
	// ======================================================================

	/**
	 * Gonderilmeyen alanlarin degerleri silinir, gonderilenler guncellenir ya da eklenir. Tamamlanmis ya da onay
	 * bekleyen asamada degistirilemez.
	 */
	@Transactional
	void syncFieldValues(long id, SyncFieldValuesRequest request) {
		BatchProcess process = find(id);
		List<SyncFieldValuesRequest.Item> items = request.fields();
		Map<Long, Field> fields = checkFieldValues(items);
		if (process.getStatus() == ProcessStatus.COMPLETED) {
			throw new BusinessRuleException(
				"Tamamlanmış aşamanın değerleri değiştirilemez. Düzeltmek için aşamayı önce geri alın.");
		}
		if (process.getStatus() == ProcessStatus.AWAITING_APPROVAL) {
			throw new BusinessRuleException(
				"Onay bekleyen aşamanın değerleri değiştirilemez. Yetkili reddederse aşama düzeltme için geri döner.");
		}
		if (process.getStatus() == ProcessStatus.RESTING) {
			throw new BusinessRuleException(
				"Dinlenmedeki aşamanın değerleri değiştirilemez. Düzeltmek için aşamayı iptal edin.");
		}
		Stage stage = stage(process);
		Set<Long> allowed = stage.processTypeId() == null ? Set.of()
			: queries.fieldsOfType(stage.processTypeId()).stream().map(Field::id).collect(Collectors.toSet());
		List<Long> incoming = items.stream().map(SyncFieldValuesRequest.Item::processTypeFieldId).toList();
		if (!allowed.containsAll(incoming)) {
			throw ValidationFailedException.of("field_values", "Process type ile uyumsuz field gönderildi.");
		}
		fieldValues.deleteOthers(id, incoming);
		for (SyncFieldValuesRequest.Item item : items) {
			Value value = fieldValue(fields.get(item.processTypeFieldId()), OperationData.normalized(item.value()));
			fieldValues.findByBatchProcessIdAndProcessTypeFieldId(id, item.processTypeFieldId()).ifPresentOrElse(
				existing -> existing.apply(value),
				() -> fieldValues.save(new BatchProcessFieldValue(id, item.processTypeFieldId(), value)));
		}
	}

	/** Alanlar var olmali; deger alanin veri tipine uymali (null serbest). */
	private Map<Long, Field> checkFieldValues(List<SyncFieldValuesRequest.Item> items) {
		ValidationErrors errors = new ValidationErrors();
		Map<Long, Field> fields = queries.fields(items.stream().map(SyncFieldValuesRequest.Item::processTypeFieldId)
			.filter(Objects::nonNull).distinct().toList());
		for (int i = 0; i < items.size(); i++) {
			Long fieldId = items.get(i).processTypeFieldId();
			if (fieldId == null) {
				errors.add("fields." + i + ".process_type_field_id", MessageKeys.REQUIRED);
			}
			else if (!fields.containsKey(fieldId)) {
				errors.add("fields." + i + ".process_type_field_id", MessageKeys.EXISTS);
			}
		}
		for (int i = 0; i < items.size(); i++) {
			Field field = fields.get(items.get(i).processTypeFieldId());
			JsonNode value = OperationData.normalized(items.get(i).value());
			if (field != null && OperationData.isPresent(value)) {
				checkFieldValue(errors, "fields." + i + ".value", field.dataType(), value);
			}
		}
		errors.throwIfAny();
		return fields;
	}

	private static void checkFieldValue(ValidationErrors errors, String path, String dataType, JsonNode value) {
		switch (dataType) {
			case "decimal" -> {
				if (OperationData.decimal(value) == null) {
					errors.add(path, "The value must be numeric for decimal fields.");
				}
			}
			case "integer" -> {
				if (OperationData.integer(value) == null) {
					errors.add(path, "The value must be an integer.");
				}
			}
			case "boolean" -> {
				if (!value.isBoolean()) {
					errors.add(path, "The value must be boolean.");
				}
			}
			case "datetime" -> {
				if (!value.isString() || IsoDateTimeDeserializer.parse(value.asString()).isEmpty()) {
					errors.add(path, "The value must be a valid datetime.");
				}
			}
			case "json" -> {
				if (!value.isContainer()) {
					errors.add(path, "The value must be an array for json fields.");
				}
			}
			default -> {
				if (!value.isString() && !value.isNumber()) {
					errors.add(path, "The value must be text.");
				}
			}
		}
	}

	/** Alanin veri tipine gore ilgili deger; {@code null} gelirse hepsi bos. */
	private static Value fieldValue(Field field, JsonNode value) {
		if (!OperationData.isPresent(value)) {
			return new Value(null, null, null, null, null, null);
		}
		return switch (field.dataType()) {
			case "decimal" -> new Value(null, OperationData.decimal(value), null, null, null, null);
			case "integer" -> new Value(null, null, OperationData.integer(value), null, null, null);
			case "boolean" -> new Value(null, null, null, value.booleanValue(), null, null);
			case "datetime" -> new Value(null, null, null, null,
				IsoDateTimeDeserializer.parse(value.asString()).map(d -> d.withNano(0)).orElse(null), null);
			case "json" -> new Value(null, null, null, null, null, value);
			default -> new Value(text(value), null, null, null, null, null);
		};
	}

	private static String text(JsonNode value) {
		return value.isNumber() ? plain(value.decimalValue()) : value.asString();
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	// ======================================================================
	// Kurallar
	// ======================================================================

	/** Onceki asamalarin hepsi tamamlanmis ya da atlanmis olmali. */
	private void assertPreviousStagesDone(BatchProcess process) {
		Map<Long, Stage> stages = siblingStages(process);
		int order = stages.get(process.getProductionStageId()).stageOrder();
		List<BatchProcess> pending = siblings(process, stages).stream()
			.filter(s -> stages.get(s.getProductionStageId()).stageOrder() < order)
			.filter(s -> s.getStatus() != ProcessStatus.COMPLETED && s.getStatus() != ProcessStatus.SKIPPED)
			.toList();
		if (!pending.isEmpty()) {
			throw new BusinessRuleException("'" + stageLabel(process) + "' aşamasına geçilemez; önceki aşamalar tamamlanmadı: "
				+ labels(pending, stages) + ".");
		}
	}

	/** Sonraki bir asama baslamisken onceki asama geri alinamaz. */
	private void assertNoLaterStageStarted(BatchProcess process) {
		Map<Long, Stage> stages = siblingStages(process);
		int order = stages.get(process.getProductionStageId()).stageOrder();
		List<BatchProcess> started = siblings(process, stages).stream()
			.filter(s -> stages.get(s.getProductionStageId()).stageOrder() > order)
			.filter(s -> s.getStatus() != ProcessStatus.WAITING)
			.toList();
		if (!started.isEmpty()) {
			throw new BusinessRuleException("'" + stageLabel(process) + "' aşaması geri alınamaz; sonraki aşamalar başlamış: "
				+ labels(started, stages) + ".");
		}
	}

	private static String labels(List<BatchProcess> rows, Map<Long, Stage> stages) {
		return rows.stream().map(s -> stages.get(s.getProductionStageId()).label()).collect(Collectors.joining(", "));
	}

	/** Tamamlanirken materyal ya da stok hareketi uretmis proses geri alinamaz. */
	private void assertNoSideEffectsToUndo(BatchProcess process, String target) {
		if (process.getStatus() != ProcessStatus.COMPLETED) {
			return;
		}
		long materials = queries.materialsCreatedBy(process.getId());
		long movements = queries.movementsOf(process.getId());
		if (materials == 0 && movements == 0) {
			return;
		}
		List<String> parts = new ArrayList<>();
		if (materials > 0) {
			parts.add(materials + " materyal");
		}
		if (movements > 0) {
			parts.add(movements + " stok hareketi");
		}
		throw new BusinessRuleException("Bu proses tamamlanırken " + String.join(" ve ", parts) + " oluşturdu; '" + target
			+ "' durumuna geri alınamaz. Önce oluşan kayıtları düzeltme (adjustment) hareketiyle geri alın.");
	}

	/** Proses tipinin zorunlu ve aktif alanlari dolu olmali. */
	private void assertRequiredFieldsFilled(BatchProcess process) {
		Stage stage = stage(process);
		if (stage.processTypeId() == null) {
			return;
		}
		Set<Long> filled = fieldValues.findByBatchProcessId(process.getId()).stream()
			.filter(BatchProcessFieldValue::isFilled).map(BatchProcessFieldValue::getProcessTypeFieldId)
			.collect(Collectors.toSet());
		List<String> missing = queries.fieldsOfType(stage.processTypeId()).stream()
			.filter(f -> f.isRequired() && f.isActive() && !filled.contains(f.id()))
			.map(Field::label)
			.toList();
		if (!missing.isEmpty()) {
			throw new BusinessRuleException(
				"Zorunlu alanlar doldurulmadan aşama tamamlanamaz: " + String.join(", ", missing) + ".");
		}
	}

	// ======================================================================
	// Parti durumu
	// ======================================================================

	/**
	 * Proseslerden: basarisiz, suren ya da dinlenmede olan varsa in_progress, hepsi bittiyse completed, hic
	 * baslamadiysa pending.
	 */
	void syncBatchStatus(long batchId) {
		ProductionBatch batch = batches.findById(batchId).orElseThrow();
		List<BatchProcess> rows = processes.findByProductionBatchId(batchId);
		boolean running = rows.stream().anyMatch(p -> p.getStatus() == ProcessStatus.FAILED
			|| p.getStatus() == ProcessStatus.IN_PROGRESS || p.getStatus() == ProcessStatus.RESTING);
		boolean finished = !rows.isEmpty() && rows.stream().allMatch(p -> p.getStatus() == ProcessStatus.COMPLETED
			|| p.getStatus() == ProcessStatus.SKIPPED);
		boolean started = rows.stream().anyMatch(p -> p.getStartedAt() != null);
		LocalDateTime now = Timestamps.now();
		if (!running && finished) {
			batch.markCompleted(now);
		}
		else if (running || started) {
			batch.markInProgress(now);
		}
		else {
			batch.markPending();
		}
	}

	// ======================================================================
	// yardimcilar
	// ======================================================================

	private BatchProcess find(long id) {
		return processes.findById(id).orElseThrow(() -> new ModelNotFoundException("BatchProcess", id));
	}

	private static StageProcess context(BatchProcess process) {
		return new StageProcess(process.getId(), process.getProductionBatchId(), process.getProductionStageId(),
			process.getNote());
	}

	private Stage stage(BatchProcess process) {
		return queries.stage(process.getProductionStageId());
	}

	private String stageLabel(BatchProcess process) {
		return stage(process).label();
	}

	private Map<Long, Stage> siblingStages(BatchProcess process) {
		List<Long> ids = new ArrayList<>(processes.findByProductionBatchId(process.getProductionBatchId()).stream()
			.map(BatchProcess::getProductionStageId).toList());
		return queries.stages(ids);
	}

	/** Partinin diger prosesleri, asama sirasina gore. */
	private List<BatchProcess> siblings(BatchProcess process, Map<Long, Stage> stages) {
		return processes.findByProductionBatchIdAndIdNot(process.getProductionBatchId(), process.getId()).stream()
			.sorted(Comparator.comparingInt(s -> stages.get(s.getProductionStageId()).stageOrder()))
			.toList();
	}

	private static Patch<LocalDateTime> date(ValidationErrors errors, String field, Patch<String> raw) {
		if (raw == null || raw.value() == null) {
			return raw == null ? null : new Patch<>(null);
		}
		Optional<LocalDateTime> parsed = IsoDateTimeDeserializer.parse(raw.value());
		if (parsed.isEmpty()) {
			errors.add(field, MessageKeys.DATE);
			return null;
		}
		return new Patch<>(parsed.get().withNano(0));
	}

}
