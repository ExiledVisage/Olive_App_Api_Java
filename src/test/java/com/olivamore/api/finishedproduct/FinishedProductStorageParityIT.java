package com.olivamore.api.finishedproduct;

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
 * Laravel'den yakalanan 86 bitmis urun deposu durumu (src/test/resources/laravel/finished-product-storage.json,
 * reference/capture_finished_product_storage.py): depo ve raf kurallari, tek ve cok rafli yerlestirme (acikta
 * sise, hacim, kilit, kapasite, pasif depo), transfer, cikis, hareket gecmisi. Laravel'in 500 verdigi iki
 * silme durumu Java'da 422 (DEVIATIONS 15).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// veri degistiriyor ve alfabetik sirada diger modullerin testlerinden once calisiyor: sonraki sinif temiz seed'li
// yeni bir veritabaniyla baslasin
@DirtiesContext
class FinishedProductStorageParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/finished-product-storage.json", port, jdbc)
			.capturedAt("2026-11-07T12:00:00Z")
			// transfer referansi rastgele UUID
			.mask("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			// raflar, raf icerigi ve depo stogu ORDER BY'siz; guncellenen satirin fiziksel yeri degisiyor (DEVIATIONS 11)
			.unorderedEverywhere("slots", "contents", "stock")
			.unordered("liste sonra", "data")
			.deviation("raf sil hareketli", 422, """
				{"message":"Bu slot silinemez; kullanıldığı yerler: 2 stok hareketi.","errors":{}}""")
			.deviation("depo sil hareketli", 422, """
				{"message":"Bu depo silinemez; kullanıldığı yerler: 14 stok hareketi. Pasife alabilirsiniz.","errors":{}}""")
			.run();
		assertThat(result.steps()).isEqualTo(86);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
