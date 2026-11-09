package com.olivamore.api.process;

import com.olivamore.api.shared.persistence.ValueEnum;

/** Proses tipinin kategorisi. */
public enum ProcessCategory implements ValueEnum {

	SOURCING("sourcing"),
	WEIGHING("weighing"),
	SORTING("sorting"),
	TRANSFER("transfer"),
	MEASUREMENT("measurement"),
	QUALITY("quality"),
	STORAGE("storage"),
	MACHINE("machine"),
	YIELD_ANALYSIS("yield_analysis"),

	/** Yalnizca eski kayitlarda var: okunur, yeni degerden secilemez. */
	MACHINE_PROCESS("machine_process");

	private final String value;

	ProcessCategory(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	public boolean assignable() {
		return this != MACHINE_PROCESS;
	}

}
