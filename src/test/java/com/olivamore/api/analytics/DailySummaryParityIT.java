package com.olivamore.api.analytics;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 17 gunluk ozet durumu (src/test/resources/laravel/daily-summary.json,
 * reference/capture_daily_summary.py): her rol icin izne gore bolumler, dolu gunler, saatli ve hatali tarih, onay
 * bekleyen asamalar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class DailySummaryParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/daily-summary.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			.run();
		assertThat(result.steps()).isEqualTo(17);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
