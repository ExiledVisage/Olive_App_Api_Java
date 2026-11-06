package com.olivamore.api.catalog;

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
 * Laravel'den yakalanan 28 tedarikci/tarla durumu (src/test/resources/laravel/supplier.json,
 * reference/capture_supplier.py) Java'ya oynatilir. Laravel'in SupplierTest'indeki ekleme, kod tekrari,
 * tarla sahipligi ve silme korumasi bunlarin icinde. Kendi veritabanini kullanir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=supplier-parity")
class SupplierParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/supplier.json", port, jdbc).run();
		assertThat(result.steps()).isEqualTo(28);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
