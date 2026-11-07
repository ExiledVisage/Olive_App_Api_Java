package com.olivamore.api;

import com.olivamore.api.devdata.SeedLoader;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Integration testlerin veritabani: canli sistemle ayni Postgres surumu, "tek basina"
 * kurulumun aynisi. Bos veritabani acilir, Flyway semayi kurar (V1, V2), sonra
 * demo veri ({@code reference/seed-data.sql}) yuklenir: {@code --seed} komutunun yaptigi.
 * <p>
 * Konteyner Spring context'iyle birlikte onbellekte tutulur; ayni context'i kullanan
 * test siniflari ayni veritabanini paylasir. Veri degistiren testler kendi
 * transaction'larinda calisip geri almali ({@code @Transactional}); HTTP uzerinden yazan parity
 * testleri ise {@code @DirtiesContext} ile isaretlenir: bitince context (ve konteyner) kapanir,
 * sonraki sinif temiz seed'li yeni bir veritabaniyla baslar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String IMAGE = "postgres:17-alpine";
	public static final Path SEED_DATA = Path.of("reference/seed-data.sql");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(IMAGE)).withDatabaseName("oliveapp");
	}

	/** Flyway'den hemen sonra, Hibernate ve testlerden once demo veri. */
	@Bean
	FlywayMigrationStrategy migrateAndSeed() {
		return flyway -> {
			flyway.migrate();
			DataSource dataSource = flyway.getConfiguration().getDataSource();
			try {
				if (SeedLoader.isEmpty(dataSource)) {
					SeedLoader.load(dataSource, SEED_DATA);
				}
			}
			catch (Exception e) {
				throw new IllegalStateException("Test veritabanina seed yuklenemedi", e);
			}
		};
	}

}
