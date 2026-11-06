package com.olivamore.api.shared.json;

import tools.jackson.databind.PropertyNamingStrategies;

/** JSON alan adlari: Java'daki camelCase adlarin Laravel'deki snake_case karsiligi. */
public final class Naming {

	private static final Snake SNAKE = new Snake();

	private Naming() {
	}

	/** {@code unitPrice -> unit_price}. Jackson'in JSON'da kullandigi donusumun aynisi. */
	public static String snake(String name) {
		return SNAKE.apply(name);
	}

	/** Jackson'in SNAKE_CASE stratejisi; {@code translate} protected oldugu icin alt sinif. */
	private static final class Snake extends PropertyNamingStrategies.SnakeCaseStrategy {

		String apply(String name) {
			return translate(name);
		}

	}

}
