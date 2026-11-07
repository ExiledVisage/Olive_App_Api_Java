package com.olivamore.api.production;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.Permissions;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.ValidatedInput;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Laravel {@code BatchProcessService}: asama durumu (katı sıra, geri alma, atlama, başarısızlık), tamamlama
 * (uzanti isleyicileri), onay akisi (gonderim, onay, red) ve partinin durumunun proseslerden turetilmesi.
 * <p>
 * Onay gerektiren asamada tamamlama "onay bekliyor"a gecer: isleyiciler gonderim aninda bir savepoint icinde
 * denenip geri alinir (kural hatasi isciye hemen doner), etki onayda islenir.
 */
@Component
class BatchProcessFlow {

	static final String AWAITING_APPROVAL = "awaiting_approval";
	static final String APPROVE_PERMISSION = "batch_process.approve";
	static final String COMPLETE_PERMISSION = "batch_process.complete";
	static final String SKIP_PERMISSION = "batch_process.skip";
	static final String REOPEN_PERMISSION = "batch_process.reopen";

	private static final String TABLE = "batch_processes";

	private final Db db;
	private final StageHandlers handlers;
	private final Permissions permissions;
	private final DataSource dataSource;

	BatchProcessFlow(Db db, StageHandlers handlers, Permissions permissions, DataSource dataSource) {
		this.db = db;
		this.handlers = handlers;
		this.permissions = permissions;
		this.dataSource = dataSource;
	}

	// ======================================================================
	// Guncelleme ve durum
	// ======================================================================

	/** Gonderilen alanlar; {@code status} varsa durum gecisi (kaydetme dahil), yoksa yalnizca kaydetme. */
	void update(Row process, ValidatedInput data, CurrentUser actor) {
		Map<String, Object> attributes = new LinkedHashMap<>(process.values());
		Map<String, Object> changes = new LinkedHashMap<>();
		if (data.has("note")) {
			changes.put("note", data.get("note"));
		}
		if (data.has("operator_id")) {
			changes.put("operator_id", data.get("operator_id"));
		}
		if (data.has("started_at")) {
			changes.put("started_at", date(data.get("started_at")));
		}
		if (data.has("completed_at")) {
			changes.put("completed_at", date(data.get("completed_at")));
		}
		attributes.putAll(changes);
		Model model = new Model(process.id(), attributes, changes);
		if (data.has("status")) {
			applyStatus(model, Php.strval(data.get("status")), data.get("operation_data"), actor);
		}
		else {
			model.save();
		}
		syncBatchStatus(Php.intval(process.get("production_batch_id")));
	}

	private void applyStatus(Model p, String status, Object operationData, CurrentUser actor) {
		if (AWAITING_APPROVAL.equals(p.status())) {
			throw new BusinessRuleException("'" + stageLabel(p.row()) + "' aşaması onay bekliyor; yetkili onaylamalı ya da reddetmeli.");
		}
		assertCanSetStatus(p.row(), status, actor);
		if (in(status, "in_progress", "completed", "skipped", "failed")) {
			assertPreviousStagesDone(p.row());
		}
		switch (status) {
			case "waiting" -> {
				assertCanReopen(p.row(), actor);
				assertNoLaterStageStarted(p.row());
				assertNoSideEffectsToUndo(p.row(), "waiting");
				p.set("status", "waiting");
				p.set("started_at", null);
				p.set("completed_at", null);
				p.set("operation_data", Db.json(null));
				p.save();
			}
			case "in_progress" -> {
				if ("completed".equals(p.status())) {
					throw new BusinessRuleException("Tamamlanmış bir batch process tekrar in_progress yapılamaz.");
				}
				p.set("status", "in_progress");
				p.setIfNull("started_at", Timestamps.now());
				p.set("completed_at", null);
				p.save();
			}
			case "completed" -> {
				if (!"completed".equals(p.status()) && requiresApproval(p.row())) {
					submitForApproval(p, operationData, actor);
				}
				else {
					complete(p, operationData);
				}
			}
			case "skipped" -> {
				assertNoSideEffectsToUndo(p.row(), "skipped");
				Row stage = stage(p.row());
				if (stage != null && Boolean.TRUE.equals(Casts.bool(stage.get("is_required")))) {
					throw new BusinessRuleException("'" + stageLabel(p.row()) + "' zorunlu bir aşama; atlanamaz.");
				}
				p.setIfNull("started_at", Timestamps.now());
				p.set("status", "skipped");
				p.setIfNull("completed_at", Timestamps.now());
				p.save();
			}
			case "failed" -> {
				p.setIfNull("started_at", Timestamps.now());
				p.set("status", "failed");
				p.setIfNull("completed_at", Timestamps.now());
				p.save();
			}
			default -> throw new BusinessRuleException("Geçersiz batch process durumu gönderildi.");
		}
	}

	/** Zaten tamamlanmissa hicbir sey yapilmaz (bu istekteki diger alanlar da kaydedilmez). */
	private void complete(Model p, Object operationData) {
		if ("completed".equals(p.status())) {
			return;
		}
		p.setIfNull("started_at", Timestamps.now());
		assertRequiredFieldsFilled(p.row());
		handlers.dispatch(p.row(), handlers.extensions(p.stageId()), asMap(operationData));
		p.set("operation_data", Db.json(phpJson(empty(operationData) ? null : operationData)));
		p.set("status", "completed");
		p.setIfNull("completed_at", Timestamps.now());
		p.save();
	}

	// ======================================================================
	// Onay akisi
	// ======================================================================

	/** Asamada isaretliyse, depo hareketi yapiyorsa ya da hattin son asamasiysa. */
	boolean requiresApproval(Row process) {
		Row stage = stage(process);
		if (Boolean.TRUE.equals(Casts.bool(stage.get("requires_approval")))) {
			return true;
		}
		if (handlers.extensions(stage.id()).stream().anyMatch(e -> e.enabled() && "storage_movement".equals(e.key()))) {
			return true;
		}
		Row max = db.first("select max(stage_order) as aggregate from production_stages where production_line_id = ?",
			stage.get("production_line_id")).orElseThrow();
		return Php.intval(stage.get("stage_order")) == Php.intval(max.get("aggregate"));
	}

	private void submitForApproval(Model p, Object operationData, CurrentUser actor) {
		p.setIfNull("started_at", Timestamps.now());
		assertRequiredFieldsFilled(p.row());
		dryRun(p.row(), p.stageId(), operationData);
		p.set("operation_data", Db.json(phpJson(empty(operationData) ? null : operationData)));
		p.set("status", AWAITING_APPROVAL);
		p.set("submitted_by_user_id", actor == null ? null : actor.id());
		p.set("submitted_at", Timestamps.now());
		p.save();
	}

	/**
	 * Kurallar gonderim aninda denenip geri alinir (Laravel: {@code DB::beginTransaction()} ... {@code finally
	 * DB::rollBack()}, yani istegin transaction'inda bir savepoint). Hata isciye hemen doner.
	 */
	private void dryRun(Row process, long stageId, Object operationData) {
		Connection connection = DataSourceUtils.getConnection(dataSource);
		Savepoint savepoint;
		try {
			savepoint = connection.setSavepoint();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Savepoint acilamadi", e);
		}
		try {
			handlers.dispatch(process, handlers.extensions(stageId), asMap(operationData));
		}
		finally {
			try {
				connection.rollback(savepoint);
			}
			catch (SQLException e) {
				throw new IllegalStateException("Savepoint'e geri donulemedi", e);
			}
		}
	}

	void approve(Row process, CurrentUser reviewer, Object note) {
		assertAwaitingApproval(process);
		assertPreviousStagesDone(process);
		Object operationData = process.get("operation_data");
		handlers.dispatch(process, handlers.extensions(Php.intval(process.get("production_stage_id"))),
			asMap(operationData == null ? List.of() : operationData));
		recordReview(process, "approved", reviewer, note);
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("status", "completed");
		values.put("completed_at", Timestamps.now());
		values.put("approved_by_user_id", reviewer.id());
		values.put("approved_at", Timestamps.now());
		db.update(TABLE, process.id(), values);
		syncBatchStatus(Php.intval(process.get("production_batch_id")));
	}

	void reject(Row process, CurrentUser reviewer, Object reason) {
		assertAwaitingApproval(process);
		recordReview(process, "rejected", reviewer, reason);
		// Gonderilmis veri korunur ki isci duzeltirken uzerinden calissin
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("status", "in_progress");
		values.put("submitted_by_user_id", null);
		values.put("submitted_at", null);
		db.update(TABLE, process.id(), values);
		syncBatchStatus(Php.intval(process.get("production_batch_id")));
	}

	private void recordReview(Row process, String decision, CurrentUser reviewer, Object note) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("decision", decision);
		values.put("reviewed_by_user_id", reviewer.id());
		values.put("submitted_by_user_id", process.get("submitted_by_user_id"));
		values.put("operation_data", Db.json(phpJson(process.get("operation_data"))));
		values.put("note", note);
		values.put("reviewed_at", Timestamps.now());
		values.put("batch_process_id", process.id());
		db.insert("batch_process_reviews", values);
	}

	private void assertAwaitingApproval(Row process) {
		if (!AWAITING_APPROVAL.equals(process.get("status"))) {
			throw new BusinessRuleException("'" + stageLabel(process) + "' aşaması onay beklemiyor (durum: "
				+ Php.strval(process.get("status")) + ").");
		}
	}

	// ======================================================================
	// Alan degerleri
	// ======================================================================

	/**
	 * Gonderilmeyen alanlarin degerleri silinir, gonderilenler updateOrCreate. Tamamlanmis ya da onay bekleyen
	 * asamada degistirilemez.
	 */
	void syncFieldValues(Row process, List<Map<String, Object>> fieldValues) {
		if ("completed".equals(process.get("status"))) {
			throw new BusinessRuleException("Tamamlanmış aşamanın değerleri değiştirilemez. Düzeltmek için aşamayı önce geri alın.");
		}
		if (AWAITING_APPROVAL.equals(process.get("status"))) {
			throw new BusinessRuleException(
				"Onay bekleyen aşamanın değerleri değiştirilemez. Yetkili reddederse aşama düzeltme için geri döner.");
		}
		Row stage = stage(process);
		List<Long> allowed = stage == null || stage.get("process_type_id") == null ? List.of()
			: db.select("select id from process_type_fields where process_type_fields.process_type_id = ?"
				+ " and process_type_fields.process_type_id is not null", stage.get("process_type_id")).stream().map(Row::id).toList();
		List<Long> incoming = new ArrayList<>();
		for (Map<String, Object> value : fieldValues) {
			Object id = value.get("process_type_field_id");
			if (StageHandlers.truthy(id)) {
				incoming.add(Php.intval(id));
			}
		}
		if (incoming.stream().anyMatch(id -> !allowed.contains(id))) {
			throw LaravelValidationException.of("field_values", "Process type ile uyumsuz field gönderildi.");
		}
		db.execute("delete from batch_process_field_values where batch_process_field_values.batch_process_id = ?"
			+ " and batch_process_field_values.batch_process_id is not null"
			+ (incoming.isEmpty() ? "" : " and process_type_field_id not in (" + com.olivamore.api.shared.persistence.Relations.inList(incoming) + ")"),
			process.id());
		for (Map<String, Object> value : fieldValues) {
			long fieldId = Php.intval(value.get("process_type_field_id"));
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("value_text", value.get("value_text"));
			values.put("value_decimal", value.get("value_decimal"));
			values.put("value_integer", value.get("value_integer"));
			values.put("value_boolean", value.get("value_boolean"));
			values.put("value_datetime", value.get("value_datetime"));
			values.put("value_json", Db.json(phpJson(value.get("value_json"))));
			Row existing = db.first("select * from batch_process_field_values where batch_process_field_values.batch_process_id = ?"
				+ " and batch_process_field_values.batch_process_id is not null and process_type_field_id = ? limit 1",
				process.id(), fieldId).orElse(null);
			if (existing == null) {
				Map<String, Object> create = new LinkedHashMap<>();
				create.put("process_type_field_id", fieldId);
				create.putAll(values);
				create.put("batch_process_id", process.id());
				db.insert("batch_process_field_values", create);
			}
			else {
				db.update("batch_process_field_values", existing.id(), values);
			}
		}
	}

	// ======================================================================
	// Kurallar
	// ======================================================================

	/** Tamamlama ve atlama ayri yetkiler (ayni endpoint). */
	private void assertCanSetStatus(Row process, String status, CurrentUser actor) {
		if (actor == null) {
			return;
		}
		String required = switch (status) {
			case "completed" -> COMPLETE_PERMISSION;
			case "skipped" -> SKIP_PERMISSION;
			default -> null;
		};
		if (required != null && !permissions.can(actor, required)) {
			throw HttpStatusException.forbidden("'" + stageLabel(process) + "' aşamasında bu işlemi yapma yetkiniz yok.");
		}
	}

	/** Baslatilmis ama bitmemis asama yetkisiz geri alinir; kapanmis asama geri acma yetkisi ister. */
	private void assertCanReopen(Row process, CurrentUser actor) {
		if (in(process.get("status"), "waiting", "in_progress")) {
			return;
		}
		if (actor == null || permissions.can(actor, REOPEN_PERMISSION)) {
			return;
		}
		throw HttpStatusException.forbidden("'" + stageLabel(process) + "' aşamasını geri açma yetkiniz yok.");
	}

	/** Onceki asamalarin hepsi tamamlanmis ya da atlanmis olmali. */
	private void assertPreviousStagesDone(Row process) {
		int order = stageOrder(process);
		List<Row> pending = siblings(process).stream()
			.filter(s -> stageOrder(s) < order)
			.filter(s -> !in(s.get("status"), "completed", "skipped"))
			.toList();
		if (!pending.isEmpty()) {
			throw new BusinessRuleException("'" + stageLabel(process) + "' aşamasına geçilemez; önceki aşamalar tamamlanmadı: "
				+ pending.stream().map(this::stageLabel).collect(Collectors.joining(", ")) + ".");
		}
	}

	/** Sonraki bir asama baslamisken onceki asama geri alinamaz. */
	private void assertNoLaterStageStarted(Row process) {
		int order = stageOrder(process);
		List<Row> started = siblings(process).stream()
			.filter(s -> stageOrder(s) > order)
			.filter(s -> !"waiting".equals(s.get("status")))
			.toList();
		if (!started.isEmpty()) {
			throw new BusinessRuleException("'" + stageLabel(process) + "' aşaması geri alınamaz; sonraki aşamalar başlamış: "
				+ started.stream().map(this::stageLabel).collect(Collectors.joining(", ")) + ".");
		}
	}

	/** Tamamlanirken materyal ya da stok hareketi uretmis proses geri alinamaz. */
	private void assertNoSideEffectsToUndo(Row process, String target) {
		if (!"completed".equals(process.get("status"))) {
			return;
		}
		long materials = db.count("select count(*) as aggregate from materials where materials.created_by_batch_process_id = ?"
			+ " and materials.created_by_batch_process_id is not null", process.id());
		long movements = db.count("select count(*) as aggregate from storage_movements where batch_process_id = ?", process.id());
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
	private void assertRequiredFieldsFilled(Row process) {
		Row stage = stage(process);
		if (stage == null || stage.get("process_type_id") == null
			|| db.find("process_types", Php.intval(stage.get("process_type_id"))).isEmpty()) {
			return;
		}
		List<Long> filled = new ArrayList<>();
		for (Row v : db.select("select * from batch_process_field_values where batch_process_field_values.batch_process_id = ?"
			+ " and batch_process_field_values.batch_process_id is not null", process.id())) {
			if (v.get("value_text") != null || v.get("value_decimal") != null || v.get("value_integer") != null
				|| v.get("value_boolean") != null || v.get("value_datetime") != null || v.get("value_json") != null) {
				filled.add(Php.intval(v.get("process_type_field_id")));
			}
		}
		List<String> missing = new ArrayList<>();
		for (Row f : db.whereIn("process_type_fields", "process_type_id", List.of(Php.intval(stage.get("process_type_id"))), "sort_order asc")) {
			if (Boolean.TRUE.equals(Casts.bool(f.get("is_required"))) && Boolean.TRUE.equals(Casts.bool(f.get("is_active")))
				&& !filled.contains(f.id())) {
				missing.add(Php.strval(f.get("label")));
			}
		}
		if (!missing.isEmpty()) {
			throw new BusinessRuleException("Zorunlu alanlar doldurulmadan aşama tamamlanamaz: " + String.join(", ", missing) + ".");
		}
	}

	// ======================================================================
	// Parti durumu
	// ======================================================================

	/** Proseslerden: basarisiz ya da suren varsa in_progress, hepsi bittiyse completed, hic baslamadiysa pending. */
	void syncBatchStatus(long batchId) {
		Row batch = db.find("production_batches", batchId).orElseThrow();
		List<Row> processes = db.select("select * from batch_processes where batch_processes.production_batch_id = ?"
			+ " and batch_processes.production_batch_id is not null", batchId);
		List<Object> statuses = processes.stream().map(p -> p.get("status")).toList();
		boolean started = processes.stream().anyMatch(p -> p.get("started_at") != null);
		Object startedAt = batch.get("started_at") != null ? batch.get("started_at") : Timestamps.now();
		Map<String, Object> values = new LinkedHashMap<>();
		if (statuses.contains("failed") || statuses.contains("in_progress")) {
			values.put("status", "in_progress");
			values.put("started_at", startedAt);
			values.put("completed_at", null);
		}
		else if (!statuses.isEmpty() && statuses.stream().allMatch(s -> "completed".equals(s) || "skipped".equals(s))) {
			values.put("status", "completed");
			values.put("started_at", startedAt);
			values.put("completed_at", batch.get("completed_at") != null ? batch.get("completed_at") : Timestamps.now());
		}
		else if (started) {
			values.put("status", "in_progress");
			values.put("started_at", startedAt);
			values.put("completed_at", null);
		}
		else {
			values.put("status", "pending");
			values.put("started_at", null);
			values.put("completed_at", null);
		}
		db.update("production_batches", batchId, values);
	}

	// ======================================================================
	// yardimcilar
	// ======================================================================

	/** Bellekteki proses modeli: degisiklikler kaydedilene kadar birikir (Eloquent save: yalnizca degisenler). */
	private final class Model {

		private final long id;
		private final Map<String, Object> attributes;
		private final Map<String, Object> changes;

		Model(long id, Map<String, Object> attributes, Map<String, Object> changes) {
			this.id = id;
			this.attributes = attributes;
			this.changes = changes;
		}

		Object status() {
			return attributes.get("status");
		}

		long stageId() {
			return Php.intval(attributes.get("production_stage_id"));
		}

		void set(String key, Object value) {
			attributes.put(key, value instanceof Db.Json j ? j.value() : value);
			changes.put(key, value);
		}

		void setIfNull(String key, Object value) {
			if (attributes.get(key) == null) {
				set(key, value);
			}
		}

		Row row() {
			return new Row(new LinkedHashMap<>(attributes));
		}

		void save() {
			if (!changes.isEmpty()) {
				db.update(TABLE, id, changes);
			}
		}

	}

	private Row stage(Row process) {
		return db.find("production_stages", Php.intval(process.get("production_stage_id"))).orElse(null);
	}

	private int stageOrder(Row process) {
		Row stage = stage(process);
		return stage == null ? 0 : (int) Php.intval(stage.get("stage_order"));
	}

	private String stageLabel(Row process) {
		Row stage = stage(process);
		return stage != null && stage.get("name") != null ? Php.strval(stage.get("name")) : "#" + Php.strval(process.get("production_stage_id"));
	}

	/** Partinin diger prosesleri, asama sirasina gore (ayni sirada olanlar sorgu sirasiyla). */
	private List<Row> siblings(Row process) {
		List<Row> rows = new ArrayList<>(db.select("select * from batch_processes where production_batch_id = ? and id != ?",
			process.get("production_batch_id"), process.id()));
		rows.sort(Comparator.comparingInt(this::stageOrder));
		return rows;
	}

	private static java.time.LocalDateTime date(Object value) {
		return value == null ? null : PhpDates.parse(Php.strval(value)).orElseThrow();
	}

	private static boolean in(Object value, String... options) {
		return value != null && List.of(options).contains(value);
	}

	/** PHP {@code $x ?: null}: bos dizi ve null bos. */
	private static boolean empty(Object value) {
		return !StageHandlers.truthy(value);
	}

	/** Istek dizisi uzerinde {@code $data['x']} erisimi: liste indisli dizi, skaler bos dizi gibi. */
	@SuppressWarnings("unchecked")
	static Map<String, Object> asMap(Object value) {
		if (value instanceof Map<?, ?> m) {
			return (Map<String, Object>) m;
		}
		Map<String, Object> out = new LinkedHashMap<>();
		if (value instanceof List<?> list) {
			for (int i = 0; i < list.size(); i++) {
				out.put(String.valueOf(i), list.get(i));
			}
		}
		return out;
	}

	/**
	 * json_encode'a girecek deger PHP'deki gibi: istekteki ondalik sayilar PHP float'i ({@code 1.10 -> 1.1}).
	 */
	static Object phpJson(Object value) {
		return switch (value) {
			case BigDecimal d -> d.doubleValue();
			case Map<?, ?> m -> {
				Map<Object, Object> out = new LinkedHashMap<>();
				m.forEach((k, v) -> out.put(k, phpJson(v)));
				yield out;
			}
			case List<?> l -> l.stream().map(BatchProcessFlow::phpJson).toList();
			case null, default -> value;
		};
	}

}
