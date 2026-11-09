package com.olivamore.api.product.productattribute;

import com.olivamore.api.shared.persistence.ValueEnum;

/** Attribute tanimlarinin veri tipi. */
public enum AttributeDataType implements ValueEnum {

	BOOLEAN("boolean"),
	INTEGER("integer"),
	DECIMAL("decimal"),
	TEXT("text"),
	JSON("json"),

	/** Yalnizca eski kayitlarda var: okunur, yeni degerden secilemez. */
	STRING("string");

	private final String value;

	AttributeDataType(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	public boolean assignable() {
		return this != STRING;
	}

}
