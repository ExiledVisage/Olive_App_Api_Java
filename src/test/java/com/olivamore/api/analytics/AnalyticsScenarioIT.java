package com.olivamore.api.analytics;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Analitik uclar: metrik katalogu, KPI ve kirilimlar, kayitli raporlar (sahiplik, dogrulama).
 * Senaryo: {@code scenarios/analytics.json}.
 */
@ApiIntegrationTest
class AnalyticsScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("analytics.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			.run()
			.assertPassed(125);
	}

}
