package com.olivamore.api.product.productiontype;

import com.olivamore.api.shared.persistence.ValueEnum;

/** Urun tipinin olcu birimi. */
public enum ProductUnit implements ValueEnum {

	KG("KG"),
	LITRE("LITRE");

	private final String value;

	ProductUnit(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
