package com.olivamore.api.productionline;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Katalog: proses tipleri ve alanlari, uretim hatlari, asamalar ve uzantilar, urun attribute eslemeleri.
 * Senaryo: {@code scenarios/catalog.json}.
 */
@ApiIntegrationTest
class CatalogScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("catalog.json", port, jdbc)
			.run()
			.assertPassed(75);
	}

}
