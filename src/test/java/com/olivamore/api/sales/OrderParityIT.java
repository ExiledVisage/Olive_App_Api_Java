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
 * Laravel'den yakalanan 91 siparis durumu (src/test/resources/laravel/order.json, reference/capture_order.py):
 * siparis CRUD (teslimat adresi musterinin mi), durum gecisleri, stok kontrolu, hazirlamada raftan alma (FIFO ve
 * elle secim), sevkiyatta fabrika stogundan dusme, hazir siparis iptalinde rafa geri koyma.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class OrderParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/order.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			// depo cevaplarinda raflar, raf icerigi ve depo stogu ORDER BY'siz (DEVIATIONS 11)
			.unorderedEverywhere("slots", "contents", "stock")
			.burn("ekle baska musterinin adresi", "orders_id_seq")
			.run();
		assertThat(result.steps()).isEqualTo(94);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
