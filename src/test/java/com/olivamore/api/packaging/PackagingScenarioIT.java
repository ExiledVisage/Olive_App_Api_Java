package com.olivamore.api.packaging;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ambalaj kalemleri, lotlar, tedarikciler ve lot hareketleri.
 * Senaryo: {@code scenarios/packaging.json}.
 */
@ApiIntegrationTest
class PackagingScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("packaging.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			.run()
			.assertPassed(116);
	}

}
