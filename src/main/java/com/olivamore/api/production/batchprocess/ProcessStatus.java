package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

/** Parti prosesinin durumu. {@link #AWAITING_APPROVAL} yalnizca onay akisinca yazilir. */
public enum ProcessStatus implements ValueEnum {

	WAITING("waiting"),
	IN_PROGRESS("in_progress"),
	AWAITING_APPROVAL("awaiting_approval"),
	COMPLETED("completed"),
	FAILED("failed"),
	SKIPPED("skipped");

	private final String value;

	ProcessStatus(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	static class StatusConverter extends ValueEnumConverter<ProcessStatus> {

		StatusConverter() {
			super(ProcessStatus.class);
		}

	}

}
