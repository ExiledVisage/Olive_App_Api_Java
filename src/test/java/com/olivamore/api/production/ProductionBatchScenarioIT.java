package com.olivamore.api.production;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Uretim partileri: olusturma, listeleme, guncelleme ve silme.
 * Senaryo: {@code scenarios/production-batch.json}.
 */
@ApiIntegrationTest
class ProductionBatchScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("production-batch.json", port, jdbc)
			// varsayilan received_at "simdi": takvim sayimi senaryonun gunune bagli
			.clockAt("2026-11-06T21:39:00Z")
			.run()
			.assertPassed(40);
	}

}
