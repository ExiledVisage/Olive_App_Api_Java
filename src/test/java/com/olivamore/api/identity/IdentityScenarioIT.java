package com.olivamore.api.identity;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Kimlik: giris, kayit, cikis, kullanicilar ve roller; rate limit basliklari da karsilastirilir.
 * Senaryo: {@code scenarios/identity.json}.
 */
@ApiIntegrationTest
class IdentityScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("identity.json", port, jdbc)
			.seededUntil("2026-11-06T15:35:11Z")
			// senaryo once admin, sonra operator1 girisiyle baslar (giris limiti sayaclari)
			.loginFirst("admin@olivamore.com", "operator1@olivamore.com")
			// access token (JWT) ve refresh token her calismada farkli; senaryoda <jwt> ve <refresh> yer tutucusu
			.mask("<jwt>|[\\w-]+\\.[\\w-]+\\.[\\w-]+")
			.mask("<refresh>|[\\w-]{43}")
			.run()
			.assertPassed(56);
	}

}
