package com.olivamore.api.shared.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code originalIsEquivalent}: degismeyen kolon yazilmaz, degisen kaybolmaz. */
class DbTest {

	@Test
	void numericLookingTextIsComparedAsText() {
		assertThat(Db.equivalent("5321234567", "05321234567")).isFalse();
		assertThat(Db.equivalent("905321234567", "+905321234567")).isFalse();
		assertThat(Db.equivalent("7", "007")).isFalse();
		assertThat(Db.equivalent("1", "1.0")).isFalse();
		assertThat(Db.equivalent("007", "007")).isTrue();
		assertThat(Db.equivalent("5", 5L)).isTrue();
	}

	@Test
	void numberColumnsAreComparedAsNumbers() {
		assertThat(Db.equivalent(new BigDecimal("0.9160"), "0.916")).isTrue();
		assertThat(Db.equivalent(new BigDecimal("100.00"), 100L)).isTrue();
		assertThat(Db.equivalent(5L, "5")).isTrue();
		assertThat(Db.equivalent(new BigDecimal("0.9160"), "0.917")).isFalse();
	}

}
