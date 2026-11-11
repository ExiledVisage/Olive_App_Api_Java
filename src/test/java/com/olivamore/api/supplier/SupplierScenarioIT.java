package com.olivamore.api.supplier;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tedarikciler: tarlalar ve teslimatlar.
 * Senaryo: {@code scenarios/supplier.json}.
 */
@ApiIntegrationTest
class SupplierScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("supplier.json", port, jdbc)
			.run()
			.assertPassed(30);
	}

}
