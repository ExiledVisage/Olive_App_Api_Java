package com.olivamore.api.sales;

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
 * Laravel'den yakalanan 70 musteri durumu (src/test/resources/laravel/customer.json, reference/capture_customer.py):
 * musteri ve adres CRUD (varsayilan adres), formul tercihleri ve siparislerden turetilen profil, silme korumalari.
 * Laravel'in NOT NULL kolona null yazip 500 verdigi uc durum Java'da alan yokmus gibi (DEVIATIONS 16).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class CustomerParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/customer.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			// adresler latest(): ayni saniyede eklenenlerde sira zamanlamaya bagli (DEVIATIONS 11)
			.unordered("adresler sonra", "data")
			.unordered("adresler son", "data")
			.deviation("guncelle aktif null", 200, null)
			.deviation("adres ekle varsayilan null", 201, null)
			.deviation("adres guncelle ulke null", 200, null)
			.run();
		assertThat(result.steps()).isEqualTo(70);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
