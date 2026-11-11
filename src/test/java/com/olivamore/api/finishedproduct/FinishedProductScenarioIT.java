package com.olivamore.api.finishedproduct;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tarifler ve son urunler: dogrulama, surumleme, bilesenler ve silme korumasi.
 * Senaryo: {@code scenarios/finished-product.json}.
 */
@ApiIntegrationTest
class FinishedProductScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("finished-product.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			// ayni saniyede olusan tarifler: tarih esitliginde sira tanimsiz
			.unordered("tarif liste sonra", "data")
			// demo urunleri ayni saniyede olustu: tarih esitliginde sira tanimsiz
			.unordered("urun liste ?is_active=1", "data")
			.run()
			.assertPassed(79);
	}

}
