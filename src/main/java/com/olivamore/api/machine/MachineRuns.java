package com.olivamore.api.machine;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Partinin makine adimlari (Sikim'daki Kirici, Malaksor...). Asama kurallarini (kronometre, adim sirasi) uretim
 * modulu uygular; burada parca secimi, parcanin mesgul olmasi, olcumler ve saatler denetlenir.
 */
@Service
public class MachineRuns {

	/** Makine sirasindaki tip, calismada girilen olcum alanlari ve secilebilir parcalari. */
	public record TypeInfo(long id, String key, String name, boolean active, List<FieldInfo> fields,
		List<PartInfo> parts) {
	}

	/** Calismada girilecek olcum alani (aktif, scope run). */
	public record FieldInfo(long id, String fieldKey, String label, String dataType, String unit, boolean isRequired,
		BigDecimal minValue, BigDecimal maxValue) {
	}

	/** Secilebilir parca; baska partide acik adimi varsa o asamanin id'si. */
	public record PartInfo(long id, String code, String name, long machineId, String machineCode, String machineName,
		Long busyBatchProcessId) {
	}

	public record Run(long id, int stepOrder, long machineTypeId, PartInfo part, LocalDateTime startedAt,
		LocalDateTime finishedAt, String note, List<RunValue> values) {
	}

	public record RunValue(long fieldId, String fieldKey, String label, String unit, JsonNode value,
		boolean outOfRange) {
	}

	private final MachineRunRepository runs;
	private final MachineTypeRepository types;
	private final MachinePartRepository parts;

	MachineRuns(MachineRunRepository runs, MachineTypeRepository types, MachinePartRepository parts) {
		this.runs = runs;
		this.types = types;
		this.parts = parts;
	}

	/** Anahtara gore tipler; olmayan anahtar haritada yok. */
	@Transactional(readOnly = true)
	public Map<String, TypeInfo> types(Collection<String> keys) {
		List<MachineType> found = types.findByKeyIn(keys);
		List<MachinePart> selectable = parts.findSelectable(found.stream().map(MachineType::getId).toList());
		Map<Long, Long> busy = runs.findByMachinePartIdInAndFinishedAtIsNull(
				selectable.stream().map(MachinePart::getId).toList()).stream()
			.collect(Collectors.toMap(r -> r.getMachinePart().getId(), MachineRun::getBatchProcessId, (a, b) -> a));
		return found.stream().collect(Collectors.toMap(MachineType::getKey, type -> new TypeInfo(type.getId(),
			type.getKey(), type.getName(), type.isActive(),
			type.getFields().stream().filter(f -> f.isActive() && !f.forMachine())
				.sorted(Comparator.comparingInt(MachineTypeField::getSortOrder))
				.map(f -> new FieldInfo(f.getId(), f.getFieldKey(), f.getLabel(), f.getDataType(), f.getUnit(),
					f.isRequired(), f.getMinValue(), f.getMaxValue()))
				.toList(),
			selectable.stream().filter(p -> p.getMachine().getMachineType().getId().equals(type.getId()))
				.map(p -> partInfo(p, busy.get(p.getId())))
				.toList())));
	}

	@Transactional(readOnly = true)
	public List<Run> runs(long batchProcessId) {
		return runs.findByBatchProcessIdOrderByStepOrder(batchProcessId).stream().map(MachineRuns::run).toList();
	}

	/** Acik adimin makine tipi adi. */
	@Transactional(readOnly = true)
	public Optional<String> openStep(long batchProcessId) {
		return runs.findByBatchProcessIdOrderByStepOrder(batchProcessId).stream().filter(MachineRun::isOpen)
			.findFirst().map(r -> r.getMachineType().getName());
	}

	/** Adim baslar; parca tipe ait, aktif ve bos olmali (uretim modulu sirayi ve kronometreyi denetler). */
	@Transactional
	public void start(long batchProcessId, int step, long machineTypeId, long partId, LocalDateTime at) {
		MachinePart part = part(machineTypeId, partId);
		if (!part.isActive() || !part.getMachine().isActive()) {
			throw new BusinessRuleException("'" + label(part) + "' pasif; seçilemez.");
		}
		if (!runs.findByMachinePartIdInAndFinishedAtIsNull(List.of(partId)).isEmpty()) {
			throw new BusinessRuleException("'" + label(part) + "' şu an başka bir partide çalışıyor.");
		}
		runs.save(new MachineRun(batchProcessId, step, part.getMachine().getMachineType(), part, at));
	}

	/** Adim biter; olcumler tipin alanlarina gore (aralik disi kaydedilir, isaretlenir). */
	@Transactional
	public void finish(long batchProcessId, int step, JsonNode values, String note, LocalDateTime at) {
		MachineRun run = find(batchProcessId, step);
		if (!run.isOpen()) {
			throw new BusinessRuleException("'" + run.getMachineType().getName() + "' adımı zaten bitirildi.");
		}
		ValidationErrors errors = new ValidationErrors();
		Map<MachineTypeField, JsonNode> checked = MachineFieldValues.runValues(run.getMachineType(), values, errors);
		errors.throwIfAny();
		run.replaceValues(checked);
		if (note != null) {
			run.changeNote(note);
		}
		run.finish(at);
	}

	/**
	 * Sonradan duzeltme: parca, saatler, not, olcumler (verilirse tamami degisir). Bitis baslangictan once olamaz;
	 * bitmis adimin bitisi silinemez.
	 */
	@Transactional
	public void correct(long batchProcessId, int step, Long partId, Patch<LocalDateTime> startedAt,
		Patch<LocalDateTime> finishedAt, Patch<String> note, JsonNode values) {
		MachineRun run = find(batchProcessId, step);
		ValidationErrors errors = new ValidationErrors();
		if (startedAt != null && startedAt.value() == null) {
			errors.add("started_at", MessageKeys.REQUIRED);
		}
		if (finishedAt != null && finishedAt.value() == null && !run.isOpen()) {
			errors.add("finished_at", MessageKeys.REQUIRED);
		}
		Map<MachineTypeField, JsonNode> checked = values == null ? null
			: MachineFieldValues.runValues(run.getMachineType(), values, errors);
		errors.throwIfAny();
		if (partId != null && !partId.equals(run.getMachinePart().getId())) {
			run.changePart(part(run.getMachineType().getId(), partId));
		}
		Patch.ifPresent(startedAt, run::changeStartedAt);
		Patch.ifPresent(finishedAt, run::changeFinishedAt);
		Patch.ifPresent(note, run::changeNote);
		if (run.getFinishedAt() != null && run.getFinishedAt().isBefore(run.getStartedAt())) {
			throw new BusinessRuleException("Makine adımının bitişi başlangıcından önce olamaz.");
		}
		if (checked != null) {
			run.replaceValues(checked);
		}
	}

	/** Asama geri alindi: adimlar ve olcumleri silinir. */
	@Transactional
	public void clear(long batchProcessId) {
		runs.deleteAll(runs.findByBatchProcessIdOrderByStepOrder(batchProcessId));
	}

	private MachineRun find(long batchProcessId, int step) {
		return runs.findByBatchProcessIdAndStepOrder(batchProcessId, step)
			.orElseThrow(() -> new BusinessRuleException(step + ". makine adımı başlatılmadı."));
	}

	private MachinePart part(long machineTypeId, long partId) {
		MachinePart part = parts.findById(partId).orElse(null);
		if (part == null || part.getMachine().getMachineType().getId() != machineTypeId) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("machine_part_id", MessageKeys.IN);
			errors.throwIfAny();
		}
		return part;
	}

	/** Tek parcali makinede makine adi, cok parcalida "Malaksor 1 / Hazne 2". */
	static String label(MachinePart part) {
		Machine machine = part.getMachine();
		return part.getName().equals(machine.getName()) ? machine.getName()
			: machine.getName() + " / " + part.getName();
	}

	private static PartInfo partInfo(MachinePart part, Long busy) {
		Machine machine = part.getMachine();
		return new PartInfo(part.getId(), part.getCode(), part.getName(), machine.getId(), machine.getCode(),
			machine.getName(), busy);
	}

	private static Run run(MachineRun run) {
		return new Run(run.getId(), run.getStepOrder(), run.getMachineType().getId(),
			partInfo(run.getMachinePart(), null), run.getStartedAt(), run.getFinishedAt(), run.getNote(),
			run.getValues().stream()
				.map(v -> new RunValue(v.getField().getId(), v.getField().getFieldKey(), v.getField().getLabel(),
					v.getField().getUnit(), v.json(), MachineFieldValues.outOfRange(v.getField(), v.json())))
				.toList());
	}

}
