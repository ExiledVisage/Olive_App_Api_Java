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
 * kurulumun aynisi. Bos veritabani acilir, Flyway semayi kurar, sonra demo veri
 * ({@code reference/seed-data.sql}) yuklenir: {@code --seed} komutunun yaptigi.
 * <p>
 * Konteyner Spring context'iyle birlikte onbellekte tutulur. {@code @ApiIntegrationTest} siniflari ayni
 * context'i ve veritabanini paylasir; her sinif {@code FreshDatabaseListener} ile demo verinin temiz haliyle
 * baslar.
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
