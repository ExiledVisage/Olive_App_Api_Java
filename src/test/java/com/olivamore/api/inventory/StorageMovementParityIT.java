package com.olivamore.api.inventory;

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
 * Laravel'den yakalanan 61 stok hareketi durumu (src/test/resources/laravel/storage-movement.json,
 * reference/capture_movement.py): acilis, giris (var olan materyal: parca/tamami; yeni materyal: partiden),
 * cikis, transfer, duzeltme, tank kurallari (izinli urun/tip, kapasite, asitlik siniri, agirlikli
 * ortalama, farkli urun/tip), materyal bolme, bakiyeler ve hareket gecmisi. Kendi veritabanini kullanir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=storage-movement-parity")
class StorageMovementParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/storage-movement.json", port, jdbc)
			// TRF-YmdHis-XXXXXX: zaman ve Str::random
			.mask("TRF-\\d{14}-[A-Z0-9]{6}")
			.run();
		assertThat(result.steps()).isEqualTo(61);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
