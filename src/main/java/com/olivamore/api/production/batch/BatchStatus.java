package com.olivamore.api.production.batch;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

/** Partinin durumu. */
public enum BatchStatus implements ValueEnum {

	PENDING("pending"),
	IN_PROGRESS("in_progress"),
	COMPLETED("completed"),
	CANCELLED("cancelled");

	private final String value;

	BatchStatus(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	@Converter
	static class StatusConverter extends ValueEnumConverter<BatchStatus> {

		StatusConverter() {
			super(BatchStatus.class);
		}

	}

}
