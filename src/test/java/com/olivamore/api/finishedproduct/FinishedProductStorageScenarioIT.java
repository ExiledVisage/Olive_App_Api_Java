package com.olivamore.api.finishedproduct;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Son urun depolari: raflar, stok giris/cikis/transfer ve silme korumasi.
 * Senaryo: {@code scenarios/finished-product-storage.json}.
 */
@ApiIntegrationTest
class FinishedProductStorageScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("finished-product-storage.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			// transfer referansi rastgele UUID
			.mask("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			// raflar, raf icerigi ve depo stogu ORDER BY'siz iliski: guncellenen satirin fiziksel yeri degisebilir
			.unorderedEverywhere("slots", "contents", "stock")
			.unordered("liste sonra", "data")
			.run()
			.assertPassed(93);
	}

}
