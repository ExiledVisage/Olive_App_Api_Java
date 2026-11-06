package com.olivamore.api.devdata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code --fresh} her seyi siler: yalnizca yerel adreslere izin verilmeli. */
class SeedCommandTest {

	@Test
	void freshIsOnlyAllowedLocally() {
		assertThat(SeedCommand.isLocal("jdbc:postgresql://localhost:5432/oliveapp")).isTrue();
		assertThat(SeedCommand.isLocal("jdbc:postgresql://127.0.0.1:55001/oliveapp?loggerLevel=OFF")).isTrue();
		assertThat(SeedCommand.isLocal("jdbc:postgresql://[::1]:5432/oliveapp")).isTrue();

		assertThat(SeedCommand.isLocal("jdbc:postgresql://161.35.54.96:5432/oliveapp")).isFalse();
		assertThat(SeedCommand.isLocal("jdbc:postgresql://db:5432/oliveapp")).isFalse();
		assertThat(SeedCommand.isLocal("jdbc:postgresql://localhost.evil.com:5432/oliveapp")).isFalse();
		assertThat(SeedCommand.isLocal("bozuk")).isFalse();
	}

}
