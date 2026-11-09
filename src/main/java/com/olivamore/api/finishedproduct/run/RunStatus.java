package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

import java.util.List;

public enum RunStatus implements ValueEnum {

	PLANNED("planned"),
	IN_PROGRESS("in_progress"),
	COMPLETED("completed"),
	CANCELLED("cancelled");

	private final String value;

	RunStatus(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	/** Bu durumdan gecilebilecek durumlar. */
	List<RunStatus> transitions() {
		return switch (this) {
			case PLANNED -> List.of(IN_PROGRESS, CANCELLED);
			case IN_PROGRESS -> List.of(COMPLETED, CANCELLED);
			case COMPLETED, CANCELLED -> List.of();
		};
	}

	@Converter
	static class StatusConverter extends ValueEnumConverter<RunStatus> {

		StatusConverter() {
			super(RunStatus.class);
		}

	}

}
