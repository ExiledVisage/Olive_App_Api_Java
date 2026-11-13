package com.olivamore.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration testlerin veritabani: canli sistemle ayni Postgres surumu, "tek basina"
 * kurulumun aynisi. Bos veritabani acilir, Flyway semayi kurar; demo veriyi
 * {@code FreshDatabaseListener} yukler.
 * <p>
 * Konteyner Spring context'iyle birlikte onbellekte tutulur. {@code @ApiIntegrationTest} siniflari ayni
 * context'i ve veritabanini paylasir; her sinif {@code FreshDatabaseListener} ile demo verinin temiz haliyle
 * baslar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String IMAGE = "postgres:17-alpine";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(IMAGE)).withDatabaseName("oliveapp");
	}

}
