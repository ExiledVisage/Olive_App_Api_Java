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
 * Laravel'den yakalanan 41 tank durumu (src/test/resources/laravel/storage-unit.json,
 * reference/capture_inventory.py) Java'ya oynatilir: ekleme/guncelleme/silme korumasi, izinli urun ve
 * tip senkronu, attribute kurallari (decimal disi, min > max, sonraki kuralda hata -> geri alma),
 * bakiyeler ve sayfali hareketler. Kendi veritabanini kullanir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=storage-unit-parity")
class StorageUnitParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/storage-unit.json", port, jdbc)
			// storage_unit_attribute_rules siralamasiz yukleniyor; reddedilen (geri alinan) insert'ten sonra
			// yeni satirlarin fiziksel yeri baglantiya bagli
			.unordered("kural ekle", "data.attribute_rules")
			.run();
		assertThat(result.steps()).isEqualTo(41);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
