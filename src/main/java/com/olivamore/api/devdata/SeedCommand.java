package com.olivamore.api.devdata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * Bos veritabanini demo veriyle doldurur ({@link DemoDataSeeder}, bugun biten bir yil), sonra uygulamayi kapatir.
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed           # veritabani bossa
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments="--seed --fresh" # once her seyi sil
 * </pre>
 * Sema uygulama acilirken Flyway ile kurulmus olur; veri calisan uygulamanin API'si uzerinden yazilir.
 * Dolu veritabanina yuklemeyi reddeder. {@code --fresh} yalnizca yerel adreslerde calisir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SeedCommand implements ApplicationRunner {

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

	private final DataSource dataSource;
	private final Flyway flyway;
	private final DemoDataSeeder seeder;
	private final ConfigurableApplicationContext context;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (!args.containsOption("seed")) {
			return;
		}
		int exitCode = 1;
		try {
			exitCode = seed(args.containsOption("fresh"));
		}
		finally {
			int code = exitCode;
			System.exit(SpringApplication.exit(context, () -> code));
		}
	}

	private int seed(boolean fresh) throws Exception {
		String url = jdbcUrl();
		if (fresh) {
			if (!isLocal(url)) {
				log.error("--fresh yalnizca yerel veritabaninda calisir, durduruldu: {}", url);
				return 1;
			}
			log.warn("--fresh: {} icindeki her sey siliniyor", url);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			jdbc.execute("DROP SCHEMA public CASCADE");
			jdbc.execute("CREATE SCHEMA public");
			flyway.migrate();
		}
		if (!seeder.isEmpty()) {
			log.error("Veritabani bos degil, seed yuklenmedi: {}. Bastan kurmak icin: --seed --fresh", url);
			return 1;
		}
		long started = System.currentTimeMillis();
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		seeder.seed(today, DemoDataSeeder.SEED, ((WebServerApplicationContext) context).getWebServer().getPort());
		log.info("Seed tamam: {} ile biten bir yil, {} ms ({})", today, System.currentTimeMillis() - started, url);
		return 0;
	}

	private String jdbcUrl() throws Exception {
		try (Connection c = dataSource.getConnection()) {
			return c.getMetaData().getURL();
		}
	}

	static boolean isLocal(String jdbcUrl) {
		if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
			return false;
		}
		try {
			String host = URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
			return host != null && LOCAL_HOSTS.contains(host);
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

}
