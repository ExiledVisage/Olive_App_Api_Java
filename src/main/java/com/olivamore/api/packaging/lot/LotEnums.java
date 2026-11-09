package com.olivamore.api.packaging.lot;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

import java.util.Set;

/** Lotlarin durum ve hareket tipleri. */
final class LotEnums {

	enum Status implements ValueEnum {

		ACTIVE("active"),
		DEPLETED("depleted");

		private final String value;

		Status(String value) {
			this.value = value;
		}

		@Override
		public String value() {
			return value;
		}

		static Status forRemaining(int remaining) {
			return remaining == 0 ? DEPLETED : ACTIVE;
		}

	}

	enum MovementType implements ValueEnum {

		IN,
		OPENING,
		ADJUSTMENT_IN,
		ADJUSTMENT_OUT,
		SCRAP,
		RETURN,
		OUT;

		private static final Set<MovementType> INCOMING = Set.of(IN, OPENING, ADJUSTMENT_IN);

		@Override
		public String value() {
			return name();
		}

		boolean incoming() {
			return INCOMING.contains(this);
		}

	}

	enum Source implements ValueEnum {

		SUPPLIER_DELIVERY,
		MANUAL_ADJUSTMENT,
		FINISHED_PRODUCT_RUN;

		@Override
		public String value() {
			return name();
		}

	}

	enum AdjustReason implements ValueEnum {

		COUNT,
		SCRAP,
		RETURN;

		@Override
		public String value() {
			return name();
		}

	}

	@Converter
	static class StatusConverter extends ValueEnumConverter<Status> {

		StatusConverter() {
			super(Status.class);
		}

	}

	@Converter
	static class MovementTypeConverter extends ValueEnumConverter<MovementType> {

		MovementTypeConverter() {
			super(MovementType.class);
		}

	}

	@Converter
	static class SourceConverter extends ValueEnumConverter<Source> {

		SourceConverter() {
			super(Source.class);
		}

	}

	private LotEnums() {
	}

}
