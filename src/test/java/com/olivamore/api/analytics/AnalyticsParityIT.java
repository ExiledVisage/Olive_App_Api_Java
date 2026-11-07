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
 * Laravel'den yakalanan 125 analitik durumu (src/test/resources/laravel/analytics.json,
 * reference/capture_analytics.py): randiman, tedarikci ve musteri ozetleri (Carbon 3 aralik sinirlari: 30/31,
 * 119/120 gun, iki yil), rapor olusturucunun butun olcu x gruplama birlesimleri, filtreler, tutarsiz secimler ve
 * kayitli raporlar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class AnalyticsParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/analytics.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			.run();
		assertThat(result.steps()).isEqualTo(125);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
