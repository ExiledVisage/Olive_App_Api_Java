package com.olivamore.api.process;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Proses tipinin kategorisi. JSON ve veritabanindaki degeri {@link #value()}. */
public enum ProcessCategory {

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

	@JsonValue
	public String value() {
		return value;
	}

	public boolean assignable() {
		return this != MACHINE_PROCESS;
	}

	public static ProcessCategory of(String value) {
		return Arrays.stream(values())
			.filter(category -> category.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Bilinmeyen proses kategorisi: " + value));
	}

}
