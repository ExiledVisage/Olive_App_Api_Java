package com.olivamore.api.notification;

import com.olivamore.api.support.ApiIntegrationTest;
import com.olivamore.api.support.ScenarioReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bildirimler: liste, okundu isaretleme ve hatirlatmalar.
 * Senaryo: {@code scenarios/notification.json}.
 */
@ApiIntegrationTest
class NotificationScenarioIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void scenario() throws Exception {
		new ScenarioReplay("notification.json", port, jdbc)
			.clockAt("2026-11-07T12:00:00Z")
			.run()
			.assertPassed(41);
	}

}
