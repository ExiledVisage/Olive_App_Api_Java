package com.olivamore.api.sales.order;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

import java.util.List;

enum OrderStatus implements ValueEnum {

	DRAFT("draft"),
	CONFIRMED("confirmed"),
	STOCK_CHECK("stock_check"),
	IN_PRODUCTION("in_production"),
	READY("ready"),
	SHIPPED("shipped"),
	CANCELLED("cancelled");

	private final String value;

	OrderStatus(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	/** Bu durumdan gecilebilecek durumlar. */
	List<OrderStatus> transitions() {
		return switch (this) {
			case DRAFT -> List.of(CONFIRMED, CANCELLED);
			case CONFIRMED -> List.of(STOCK_CHECK, CANCELLED);
			case STOCK_CHECK -> List.of(READY, IN_PRODUCTION, CANCELLED);
			case IN_PRODUCTION -> List.of(READY, CANCELLED);
			case READY -> List.of(SHIPPED, CANCELLED);
			case SHIPPED, CANCELLED -> List.of();
		};
	}

	boolean editable() {
		return this == DRAFT || this == CONFIRMED;
	}

	@Converter
	static class StatusConverter extends ValueEnumConverter<OrderStatus> {

		StatusConverter() {
			super(OrderStatus.class);
		}

	}

}
