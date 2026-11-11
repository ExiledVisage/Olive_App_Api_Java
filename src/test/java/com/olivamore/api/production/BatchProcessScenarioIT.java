package com.olivamore.api.production;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Uretim asamalari bastan sona: sira, tartim, donusum, analiz, depo hareketi, onay/red, geri alma.
 * Senaryo: {@code scenarios/batch-process.json}.
 */
@ApiIntegrationTest
class BatchProcessScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("batch-process.json", port, jdbc)
			.run()
			.assertPassed(110);
	}

}
