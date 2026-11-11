package com.olivamore.api.inventory;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Materyal listesi, filtreler, detay ve soy grafigi.
 * Senaryo: {@code scenarios/material.json}.
 */
@ApiIntegrationTest
class MaterialScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("material.json", port, jdbc)
			.run()
			.assertPassed(30);
	}

}
