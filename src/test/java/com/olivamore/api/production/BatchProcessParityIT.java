package com.olivamore.api.production;

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
 * Laravel'den yakalanan 108 proses durumu (src/test/resources/laravel/batch-process.json,
 * reference/capture_batch_process.py): yeni bir partinin 12 asamasi sirayla; kati sira, yetkiler (tamamlama,
 * atlama, geri acma, onay), alan degerleri ve tipleri, tartim/donusum/analiz/depo hareketi isleyicileri,
 * onay akisi (gonderimde deneme, red, onay), parti durumunun proseslerden turetilmesi.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=batch-process-parity")
class BatchProcessParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/batch-process.json", port, jdbc)
			// Partinin prosesleri ve materyalleri siralamasiz iliski: guncellenen satirlarin fiziksel yeri iki
			// veritabaninda farkli (DEVIATIONS 11)
			.unordered("parti tamamlandi", "data.processes")
			.unordered("parti tamamlandi", "data.materials")
			.unordered("parti atlanmis", "data.processes")
			.unordered("parti atlanmis", "data.materials")
			.run();
		assertThat(result.steps()).isEqualTo(110);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
