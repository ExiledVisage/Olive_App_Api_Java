package com.olivamore.api.sales;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Musteriler: adresler, iletisim, formul tercihleri.
 * Senaryo: {@code scenarios/customer.json}.
 */
@ApiIntegrationTest
class CustomerScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("customer.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			// adresler en yeniden eskiye: ayni saniyede eklenenlerde sira tanimsiz
			.run()
			.assertPassed(72);
	}

}
