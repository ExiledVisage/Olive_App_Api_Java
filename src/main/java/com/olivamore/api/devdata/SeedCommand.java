package com.olivamore.api.devdata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Set;

/**
 * Bos veritabanini demo veriyle doldurur, sonra uygulamayi kapatir.
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed           # veritabani bossa
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments="--seed --fresh" # once her seyi sil
 * </pre>
 * Sema uygulama acilirken Flyway ile kurulmus olur (V1, V2); bu komut yalnizca veriyi yukler.
 * Dolu veritabanina yuklemeyi reddeder. {@code --fresh} yalnizca yerel adreslerde calisir.
 * {@code laravel} profilinde hic calismaz: o veritabani Laravel'in (kopru de localhost'ta
 * oldugu icin adres kontrolu yetmez); onu seed-demo.sh ve reference/restore.sh yonetir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SeedCommand implements ApplicationRunner {

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

	private final DataSource dataSource;
	private final Flyway flyway;
	private final ConfigurableApplicationContext context;
	private final Environment environment;

	@Value("${olive.seed.file:reference/seed-data.sql}")
	private Path seedFile;

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
		if (environment.acceptsProfiles(Profiles.of("laravel"))) {
			log.error("laravel profilinde seed calismaz: veritabani Laravel'in. Laravel tarafinda: reference/restore.sh");
			return 1;
		}
		String url = jdbcUrl();
		if (!Files.isRegularFile(seedFile)) {
			log.error("Seed dosyasi bulunamadi: {} (calisma klasoru: {})", seedFile.toAbsolutePath(), Path.of("").toAbsolutePath());
			return 1;
		}
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
		if (!SeedLoader.isEmpty(dataSource)) {
			log.error("Veritabani bos degil, seed yuklenmedi: {}. Bastan kurmak icin: --seed --fresh", url);
			return 1;
		}
		long started = System.currentTimeMillis();
		long rows = SeedLoader.load(dataSource, seedFile);
		log.info("Seed tamam: {} satir, {} ms ({} -> {})", rows, System.currentTimeMillis() - started, seedFile, url);
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
