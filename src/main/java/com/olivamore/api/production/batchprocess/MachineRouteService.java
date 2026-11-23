package com.olivamore.api.production.batchprocess;

import com.olivamore.api.machine.MachineRuns;
import com.olivamore.api.production.batch.ProductionBatch;
import com.olivamore.api.production.batch.ProductionBatchRepository;
import com.olivamore.api.production.batchprocess.stage.StageExtensions;
import com.olivamore.api.production.batchprocess.stage.StageQueries;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Makine hatti ({@code machine_route}) uzantili asamada (Sikim) partinin makine adimlari. Asamanin kronometresi
 * kalir; makine adimlari onun icinde ayri kronometrelerdir (docs/MACHINES.md):
 * <ul>
 * <li>adim, asama kronometresi calisirken baslar (baslamadan da bittikten sonra da baslamaz);</li>
 * <li>adim N, adim N-1 baslamadan baslamaz; saatler ortusebilir (surekli akis);</li>
 * <li>parca ayni anda tek partide calisir; tipte tek secenek varsa kendiliginden secilir;</li>
 * <li>acik adim varken asama kronometresi bitmez ({@link BatchProcessService}).</li>
 * </ul>
 * Baslatilmamis adim asamanin tamamlanmasini engellemez ("2/4" olarak gorunur).
 */
@Service
class MachineRouteService {

	/** Asamanin makine adimlari: tamamlanan / toplam, kronometre durumu, sirali adimlar. */
	record Route(long batchProcessId, int completed, int total, boolean timerStarted, boolean timerFinished,
		List<Step> steps) {
	}

	/**
	 * Adim: {@code waiting} (baslamadi), {@code running}, {@code done}. Tip tanimsizsa {@code machine_type} bos;
	 * {@code parts} secilebilir parcalar, mesgulse {@code busy_batch_code}.
	 */
	record Step(int stepOrder, String machineTypeKey, MachineType machineType, String status,
		List<MachineRuns.FieldInfo> fields, List<Part> parts, MachineRuns.Run run) {
	}

	record MachineType(long id, String key, String name, boolean isActive) {
	}

	record Part(long id, String code, String name, long machineId, String machineCode, String machineName,
		String busyBatchCode) {
	}

	private final BatchProcessRepository processes;
	private final ProductionBatchRepository batches;
	private final StageQueries queries;
	private final MachineRuns machineRuns;

	MachineRouteService(BatchProcessRepository processes, ProductionBatchRepository batches, StageQueries queries,
		MachineRuns machineRuns) {
		this.processes = processes;
		this.batches = batches;
		this.queries = queries;
		this.machineRuns = machineRuns;
	}

	@Transactional(readOnly = true)
	Route route(long id) {
		BatchProcess process = find(id);
		List<String> keys = keys(process);
		Map<String, MachineRuns.TypeInfo> types = machineRuns.types(keys);
		Map<Integer, MachineRuns.Run> runs = machineRuns.runs(id).stream()
			.collect(Collectors.toMap(MachineRuns.Run::stepOrder, Function.identity()));
		Map<Long, String> busyCodes = batchCodes(types.values().stream().flatMap(t -> t.parts().stream())
			.map(MachineRuns.PartInfo::busyBatchProcessId).filter(Objects::nonNull).distinct().toList());
		List<Step> steps = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			MachineRuns.TypeInfo type = types.get(keys.get(i));
			MachineRuns.Run run = runs.get(i + 1);
			String status = run == null ? "waiting" : run.finishedAt() == null ? "running" : "done";
			steps.add(new Step(i + 1, keys.get(i),
				type == null ? null : new MachineType(type.id(), type.key(), type.name(), type.active()), status,
				type == null ? List.of() : type.fields(),
				type == null ? List.of() : type.parts().stream().map(p -> new Part(p.id(), p.code(), p.name(),
					p.machineId(), p.machineCode(), p.machineName(),
					p.busyBatchProcessId() == null || p.busyBatchProcessId() == id ? null
						: busyCodes.get(p.busyBatchProcessId()))).toList(),
				run));
		}
		int completed = (int) steps.stream().filter(s -> "done".equals(s.status())).count();
		return new Route(id, completed, steps.size(), process.getMachineStartedAt() != null,
			process.getMachineFinishedAt() != null, steps);
	}

	/** Parca verilmezse tipin tek secilebilir parcasi; birden coksa secim zorunlu. */
	@Transactional
	void start(long id, int step, Long partId) {
		BatchProcess process = find(id);
		List<String> keys = keys(process);
		String key = stepKey(keys, step);
		String stage = stageLabel(process);
		if (process.getStatus() != ProcessStatus.WAITING && process.getStatus() != ProcessStatus.IN_PROGRESS) {
			throw new BusinessRuleException("'" + stage + "' aşaması açık değil; makine adımı başlatılamaz.");
		}
		if (process.getMachineStartedAt() == null) {
			throw new BusinessRuleException("'" + stage + "' aşamasında önce kronometreyi başlatın; makine adımları "
				+ "kronometre çalışırken başlar.");
		}
		if (process.getMachineFinishedAt() != null) {
			throw new BusinessRuleException("'" + stage + "' aşamasında kronometre bitti; makine adımı başlatılamaz.");
		}
		Map<String, MachineRuns.TypeInfo> types = machineRuns.types(keys);
		MachineRuns.TypeInfo type = types.get(key);
		if (type == null || !type.active()) {
			throw new BusinessRuleException("'" + key + "' makine tipi tanımlı değil ya da pasif; aşamanın makine "
				+ "sırasını düzeltin.");
		}
		Map<Integer, MachineRuns.Run> runs = machineRuns.runs(id).stream()
			.collect(Collectors.toMap(MachineRuns.Run::stepOrder, Function.identity()));
		if (runs.containsKey(step)) {
			throw new BusinessRuleException("'" + type.name() + "' adımı zaten başlatıldı.");
		}
		if (step > 1 && !runs.containsKey(step - 1)) {
			MachineRuns.TypeInfo previous = types.get(keys.get(step - 2));
			throw new BusinessRuleException("'" + type.name() + "' adımı, '"
				+ (previous == null ? keys.get(step - 2) : previous.name()) + "' adımı başlamadan başlatılamaz.");
		}
		MachineRuns.PartInfo part = choosePart(type, partId);
		if (part.busyBatchProcessId() != null) {
			String code = batchCodes(List.of(part.busyBatchProcessId())).get(part.busyBatchProcessId());
			throw new BusinessRuleException("'" + partLabel(part) + "' şu an " + code + " partisinde çalışıyor; "
				+ "o adım bitince seçilebilir.");
		}
		machineRuns.start(id, step, type.id(), part.id(), Timestamps.now());
	}

	@Transactional
	void finish(long id, int step, JsonNode values, String note) {
		BatchProcess process = find(id);
		stepKey(keys(process), step);
		machineRuns.finish(id, step, values, note, Timestamps.now());
	}

	/** Saat, parca, not ve olcum duzeltmesi; asama tamamlanmis olsa da yapilir (stoga etkisi yok). */
	@Transactional
	void correct(long id, int step, CorrectMachineRunRequest request) {
		BatchProcess process = find(id);
		stepKey(keys(process), step);
		ValidationErrors errors = new ValidationErrors();
		Patch<LocalDateTime> startedAt = BatchProcessService.date(errors, "started_at", request.startedAt());
		Patch<LocalDateTime> finishedAt = BatchProcessService.date(errors, "finished_at", request.finishedAt());
		errors.throwIfAny();
		machineRuns.correct(id, step, request.machinePartId(), startedAt, finishedAt, request.note(),
			request.values());
	}

	/** Asamanin makine sirasi (tip anahtarlari); uzanti yoksa 422. */
	private List<String> keys(BatchProcess process) {
		List<String> keys = StageExtensions.machineRoute(queries.extensions(process.getProductionStageId()));
		if (keys == null) {
			throw new BusinessRuleException("'" + stageLabel(process) + "' aşamasında makine hattı yok.");
		}
		return keys;
	}

	private static String stepKey(List<String> keys, int step) {
		if (step < 1 || step > keys.size()) {
			throw new HttpStatusException(404, "Bu aşamada " + step + ". makine adımı yok.");
		}
		return keys.get(step - 1);
	}

	private static MachineRuns.PartInfo choosePart(MachineRuns.TypeInfo type, Long partId) {
		if (partId != null) {
			return type.parts().stream().filter(p -> p.id() == partId).findFirst().orElseGet(() -> {
				ValidationErrors errors = new ValidationErrors();
				errors.add("machine_part_id", MessageKeys.IN);
				errors.throwIfAny();
				return null;
			});
		}
		if (type.parts().isEmpty()) {
			throw new BusinessRuleException("'" + type.name() + "' tipinde çalışabilir (aktif) makine yok.");
		}
		if (type.parts().size() > 1) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("machine_part_id", MessageKeys.REQUIRED);
			errors.throwIfAny();
		}
		return type.parts().getFirst();
	}

	private static String partLabel(MachineRuns.PartInfo part) {
		return part.name().equals(part.machineName()) ? part.machineName()
			: part.machineName() + " / " + part.name();
	}

	private Map<Long, String> batchCodes(List<Long> processIds) {
		if (processIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> batchOf = processes.findAllById(processIds).stream()
			.collect(Collectors.toMap(BatchProcess::getId, BatchProcess::getProductionBatchId));
		Map<Long, String> codes = batches.findAllById(batchOf.values()).stream()
			.collect(Collectors.toMap(ProductionBatch::getId, ProductionBatch::getBatchCode));
		return batchOf.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
			e -> codes.getOrDefault(e.getValue(), "başka bir")));
	}

	private String stageLabel(BatchProcess process) {
		return queries.stage(process.getProductionStageId()).label();
	}

	private BatchProcess find(long id) {
		return processes.findById(id).orElseThrow(() -> new ModelNotFoundException("BatchProcess", id));
	}

}
