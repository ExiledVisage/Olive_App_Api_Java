package com.olivamore.api.finishedproduct;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dolum kosulari: planlama kurallari (tarif orani, fire, asitlik, ambalaj), tahsis, durum gecisleri ve stok etkisi.
 * Senaryo: {@code scenarios/finished-product-run.json}.
 */
@ApiIntegrationTest
class FinishedProductRunScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("finished-product-run.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			// lotun kosulari ORDER BY'siz iliski: guncellenen satirin fiziksel yeri degisebilir
			.unordered("lot 330 tamamlamadan sonra", "data.runs")
			// ayni saniyede olusan kosular: tarih esitliginde sira tanimsiz
			.unordered("liste sonra", "content")
			.run()
			.assertPassed(95);
	}

}
