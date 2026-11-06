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
