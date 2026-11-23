package com.olivamore.api.machine;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Partinin bir makine adimi: asamanin makine sirasindaki yeri, calistigi parca, sure ve olcumler. */
@Entity
@Table(name = "machine_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MachineRun extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long batchProcessId;

	@Column(nullable = false)
	private int stepOrder;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "machine_type_id")
	private MachineType machineType;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "machine_part_id")
	private MachinePart machinePart;

	@Column(nullable = false)
	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

	private String note;

	@OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<MachineRunValue> values = new ArrayList<>();

	MachineRun(long batchProcessId, int stepOrder, MachineType machineType, MachinePart machinePart,
		LocalDateTime startedAt) {
		this.batchProcessId = batchProcessId;
		this.stepOrder = stepOrder;
		this.machineType = machineType;
		this.machinePart = machinePart;
		this.startedAt = startedAt;
	}

	boolean isOpen() {
		return finishedAt == null;
	}

	void finish(LocalDateTime at) {
		finishedAt = at;
	}

	void changeStartedAt(LocalDateTime value) {
		startedAt = value;
	}

	void changeFinishedAt(LocalDateTime value) {
		finishedAt = value;
	}

	void changePart(MachinePart value) {
		machinePart = value;
	}

	void changeNote(String value) {
		note = value;
	}

	/** Olcumlerin tamami verilenlerle degisir (ayni alanin satiri guncellenir). */
	void replaceValues(Map<MachineTypeField, JsonNode> given) {
		values.removeIf(value -> !given.containsKey(value.getField()));
		given.forEach((field, json) -> values.stream().filter(v -> v.getField().equals(field)).findFirst()
			.ifPresentOrElse(v -> v.set(json), () -> values.add(new MachineRunValue(this, field, json))));
	}

}
