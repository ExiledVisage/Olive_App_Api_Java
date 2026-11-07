package com.olivamore.api.packaging;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 113 ambalaj durumu (src/test/resources/laravel/packaging.json,
 * reference/capture_packaging.py): urun tedarikcileri, ambalaj kalemleri (devir lotu), lot listesi ve
 * detayi (hareketler, dolum kosulari), mal girisi, kunye duzeltmesi, sayim duzeltmesi/fire/iade, silme
 * korumalari; dort kullaniciyla.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PackagingParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/packaging.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			.run();
		assertThat(result.steps()).isEqualTo(113);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
