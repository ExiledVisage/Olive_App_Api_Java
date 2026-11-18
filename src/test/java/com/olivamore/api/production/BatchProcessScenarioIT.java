package com.olivamore.api.production;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Uretim asamalari bastan sona: sira, tartim, donusum, analiz, on dinlendirme (dinlenme, iptal, Boşalt, tortu),
 * onay/red, geri alma.
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
			.mask("TRF-\\d{14}-[A-Z0-9]{6}")
			.run()
			.assertPassed(129);
	}

}
