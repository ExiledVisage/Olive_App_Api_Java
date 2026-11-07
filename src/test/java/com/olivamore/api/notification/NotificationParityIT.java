package com.olivamore.api.notification;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 38 bildirim durumu (src/test/resources/laravel/notification.json,
 * reference/capture_notification.py): durtme (rol ya da kisi, kendini uyarma, bekleme suresi, govde), bildirim
 * listesi (okunmamis, sayfali), okunmamis sayisi, okundu isaretleme, durtulecek kisiler.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class NotificationParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/notification.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			.run();
		assertThat(result.steps()).isEqualTo(38);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
