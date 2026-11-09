package com.olivamore.api.process;

import com.olivamore.api.shared.persistence.ValueEnum;

/** Proses alaninin veri tipi. */
public enum FieldDataType implements ValueEnum {

	TEXT("text"),
	DECIMAL("decimal"),
	INTEGER("integer"),
	BOOLEAN("boolean"),
	DATETIME("datetime"),
	JSON("json");

	private final String value;

	FieldDataType(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
