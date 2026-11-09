package com.olivamore.api.finishedproduct;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan dolum kosusu durumu (src/test/resources/laravel/finished-product-run.json,
 * reference/capture_finished_product_run.py): tank ve tarif kontrolleri, sise/yag dengesi, tahmini attribute
 * araligi, FIFO ve elle ambalaj tahsisi, durum gecisleri, tamamlamada ambalaj ve yag tuketimi (tank bakiyesi,
 * lot hareketleri), planlamadan sonra degisen tank ve lot, silme.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// veri degistiriyor ve alfabetik sirada diger modullerin testlerinden once calisiyor: sonraki sinif temiz seed'li
// yeni bir veritabaniyla baslasin
@DirtiesContext
class FinishedProductRunParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/finished-product-run.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			// lotun kosulari ORDER BY'siz; silinen tahsis satirlarindan sonra fiziksel sira baglantiya bagli (DEVIATIONS 11)
			.unordered("lot 330 tamamlamadan sonra", "data.runs")
			// ayni saniyede olusan kosular: latest() esitlikte sirasiz (guncellenen satir tablonun sonuna gider)
			.unordered("liste sonra", "data")
			.run();
		assertThat(result.steps()).isEqualTo(95);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
