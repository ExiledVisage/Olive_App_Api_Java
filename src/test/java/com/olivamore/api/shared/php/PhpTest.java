package com.olivamore.api.shared.php;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** PHP donusumleri; beklenenler PHP 8.4'un davranisi. */
class PhpTest {

	@Test
	void intval() {
		assertThat(Php.intval(7L)).isEqualTo(7);
		assertThat(Php.intval("7abc")).isEqualTo(7);
		assertThat(Php.intval("  12.9")).isEqualTo(12);
		assertThat(Php.intval("1e3")).isEqualTo(1000);
		assertThat(Php.intval("-5x")).isEqualTo(-5);
		assertThat(Php.intval("abc")).isZero();
		assertThat(Php.intval(new BigDecimal("7.9"))).isEqualTo(7);
		assertThat(Php.intval(new BigDecimal("-7.9"))).isEqualTo(-7);
		assertThat(Php.intval(true)).isEqualTo(1);
		assertThat(Php.intval(false)).isZero();
		assertThat(Php.intval(null)).isZero();
		assertThat(Php.intval(List.of())).isZero();
		assertThat(Php.intval(List.of(1L))).isEqualTo(1);
		assertThat(Php.intval("99999999999999999999")).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	void strval() {
		assertThat(Php.strval(106L)).isEqualTo("106");
		assertThat(Php.strval(true)).isEqualTo("1");
		assertThat(Php.strval(false)).isEmpty();
		assertThat(Php.strval(null)).isEmpty();
		assertThat(Php.strval(new BigDecimal("1.0"))).isEqualTo("1");
		assertThat(Php.strval(new BigDecimal("1.50"))).isEqualTo("1.5");
		assertThat(Php.strval(Map.of("a", 1))).isEqualTo("Array");
	}

	@Test
	void floats() {
		// PHP 8.4'ten: var_dump((string)(3200*0.915), (string)(0.1+0.2), (string)1e25, (string)0.00001, ...)
		assertThat(Php.floatString(3200 * 0.915)).isEqualTo("2928");
		assertThat(Php.floatString(0.1 + 0.2)).isEqualTo("0.3");
		assertThat(Php.floatString(1e25)).isEqualTo("1.0E+25");
		assertThat(Php.floatString(0.00001)).isEqualTo("1.0E-5");
		assertThat(Php.floatString(123456789012345678.0)).isEqualTo("1.2345678901235E+17");
		assertThat(Php.floatString(1.0 / 3)).isEqualTo("0.33333333333333");
		assertThat(Php.floatString(2.5)).isEqualTo("2.5");
		assertThat(Php.floatString(-7.0)).isEqualTo("-7");
		assertThat(Php.floatval("12abc")).isEqualTo(12);
		assertThat(Php.floatval(" 3.5")).isEqualTo(3.5);
		assertThat(Php.floatval("1e3x")).isEqualTo(1000);
		// json_encode([3200*0.915, 0.1+0.2, 2928.0]) -> [2928,0.30000000000000004,2928]
		assertThat(Php.jsonFloat(3200 * 0.915)).isEqualTo(2928L);
		assertThat(Php.jsonFloat(0.1 + 0.2)).isEqualTo(0.30000000000000004);
		assertThat(Php.jsonFloat(2928.0)).isEqualTo(2928L);
	}

	@Test
	void round() {
		// PHP 8.4: round(0.285, 2), round(1.955, 2), round(-2.5, 0), round(1/3*100, 2)
		assertThat(Php.round(0.285, 2)).isEqualTo(0.29);
		assertThat(Php.round(1.955, 2)).isEqualTo(1.96);
		assertThat(Php.round(-2.5, 0)).isEqualTo(-3.0);
		assertThat(Php.round(1.0 / 3 * 100, 2)).isEqualTo(33.33);
		assertThat(Php.round(3990.07 / 6000 * 100, 2)).isEqualTo(66.5);
	}

	@Test
	void basename() {
		assertThat(Php.basename("../../../.env")).isEqualTo(".env");
		assertThat(Php.basename("sub/olivamore.apk")).isEqualTo("olivamore.apk");
		assertThat(Php.basename("a/b/")).isEqualTo("b");
		assertThat(Php.basename("..\\..\\x.apk")).isEqualTo("..\\..\\x.apk"); // Linux PHP: ters bolu ayirici degil
	}

	@Test
	void ascii() {
		assertThat(Php.ascii("1.0-şık")).isEqualTo("1.0-sik");
		assertThat(Php.ascii("Çğİöü")).isEqualTo("CgIou");
		assertThat(Php.ascii("plain-1.0")).isEqualTo("plain-1.0");
	}

}
