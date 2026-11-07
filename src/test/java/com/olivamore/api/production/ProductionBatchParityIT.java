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
 * Laravel'den yakalanan 36 uretim partisi durumu (src/test/resources/laravel/production-batch.json,
 * reference/capture_production_batch.py): liste ve takvim suzgecleri, hattin girdi kurallari, cikti
 * tiplerinin cozulmesi, asamalardan prosesler, tedarikci/tarla, tarih cast'i, silme korumasi.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=production-batch-parity")
class ProductionBatchParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/production-batch.json", port, jdbc).run();
		assertThat(result.steps()).isEqualTo(36);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
