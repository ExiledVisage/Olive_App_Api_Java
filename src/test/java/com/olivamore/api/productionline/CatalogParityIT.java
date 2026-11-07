package com.olivamore.api.productionline;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 77 katalog durumu (src/test/resources/laravel/catalog.json,
 * reference/capture_catalog.py) ayni sirayla Java'ya oynatilir. Laravel'deki hatalar icin Java'nin
 * dogru davranisi deviation'larda (DEVIATIONS.md). Kendi veritabanini kullanir (veri degistirir).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "olive.test.context=catalog-parity")
class CatalogParityIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/catalog.json", port, jdbc)
			// unique kuralinda whereHas -> 500. Java: esleme olusur (200, ->fresh()).
			.deviation("esleme ekle", 200, null)
			.deviation("esleme mevcut global anahtar", 200, null)
			// unique kurali olmayan kolona bakiyor -> 500. Java: anahtar bu kategoride yoksa yok sayilir.
			.deviation("esleme guncelle anahtar", 200, null)
			// unique kurali route('processType') = null -> veritabani kisiti 500. Java: 422.
			.deviation("alan tekrar anahtar", 422, """
				{"message":"The field key has already been taken.","errors":{"field_key":["The field key has already been taken."]}}""")
			// Laravel insert'e kadar gidip sequence tuketti; Java validation'da reddediyor
			.burn("alan tekrar anahtar", "process_type_fields_id_seq")
			.run();
		assertThat(result.steps()).isEqualTo(77);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

	/**
	 * Laravel'de UpdateProcessTypeRequest ignore() icin route('processType') kullaniyor (parametre adi
	 * process_type): bir proses tipini kendi adiyla guncellemek "already been taken" 422 veriyor. Java: 200.
	 */
	@Test
	void processTypeCanKeepItsOwnName() {
		RestClient http = RestClient.create("http://localhost:" + port);
		String token = JSON.readTree(LaravelReplay.call(http, "POST", "/api/auth/login", null,
			JSON.readTree("{\"email\":\"admin@olivamore.com\",\"password\":\"123\"}")).body()).get("token").asString();
		String name = jdbc.queryForObject("select name from process_types where id = 1", String.class);
		LaravelReplay.Response same = LaravelReplay.call(http, "PATCH", "/api/process-types/1", token,
			JSON.readTree("{\"name\":\"" + name + "\"}"));
		assertThat(same.status()).as(same.body()).isEqualTo(200);
		String other = jdbc.queryForObject("select name from process_types where id = 2", String.class);
		LaravelReplay.Response taken = LaravelReplay.call(http, "PATCH", "/api/process-types/1", token,
			JSON.readTree("{\"name\":\"" + other + "\"}"));
		assertThat(taken.status()).isEqualTo(422);
		assertThat(taken.body()).contains("The name has already been taken.");
	}

}
