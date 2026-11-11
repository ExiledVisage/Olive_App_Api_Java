package com.olivamore.api.sales;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Siparisler: dogrulama, duzenleme, durum gecisleri, raftan toplama ve sevk.
 * Senaryo: {@code scenarios/order.json}.
 */
@ApiIntegrationTest
class OrderScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("order.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			.run()
			.assertPassed(94);
	}

}
