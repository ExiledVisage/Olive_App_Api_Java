package com.olivamore.api.analytics;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gunluk ozet: tarih secimi ve rol bazli bolumler.
 * Senaryo: {@code scenarios/daily-summary.json}.
 */
@ApiIntegrationTest
class DailySummaryScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("daily-summary.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			.run()
			.assertPassed(17);
	}

}
