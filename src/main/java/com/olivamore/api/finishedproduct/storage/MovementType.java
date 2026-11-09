package com.olivamore.api.finishedproduct.storage;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

import java.util.Set;

enum MovementType implements ValueEnum {

	STOCK_IN("stock_in"),
	STOCK_OUT("stock_out"),
	TRANSFER_IN("transfer_in"),
	TRANSFER_OUT("transfer_out"),
	ORDER_PICK("order_pick"),
	ORDER_RETURN("order_return");

	private static final Set<MovementType> INCOMING = Set.of(STOCK_IN, TRANSFER_IN, ORDER_RETURN);

	private final String value;

	MovementType(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	/** Rafa giren hareket mi. */
	boolean incoming() {
		return INCOMING.contains(this);
	}

	@Converter
	static class TypeConverter extends ValueEnumConverter<MovementType> {

		TypeConverter() {
			super(MovementType.class);
		}

	}

}
