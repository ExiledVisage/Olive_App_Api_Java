package com.olivamore.api.inventory;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tank hareketleri: acilis, giris, cikis, transfer, sayim duzeltmesi; kapasite, izin ve attribute kurallari.
 * Senaryo: {@code scenarios/storage-movement.json}.
 */
@ApiIntegrationTest
class StorageMovementScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("storage-movement.json", port, jdbc)
			// TRF-YmdHis-XXXXXX: zaman ve rastgele ek
			.mask("TRF-\\d{14}-[A-Z0-9]{6}")
			.run()
			.assertPassed(61);
	}

}
