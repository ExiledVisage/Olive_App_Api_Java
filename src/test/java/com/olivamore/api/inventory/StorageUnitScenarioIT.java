package com.olivamore.api.inventory;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tanklar: ekleme/guncelleme/silme korumasi, izinli urun ve tip, attribute kurallari, bakiyeler ve hareketler.
 * Senaryo: {@code scenarios/storage-unit.json}.
 */
@ApiIntegrationTest
class StorageUnitScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("storage-unit.json", port, jdbc)
			.run()
			.assertPassed(54);
	}

}
