package com.olivamore.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Integration testlerin veritabani: Laravel ve canli sistemle ayni Postgres surumu,
 * dondurulmus referans veri (reference/reference-data.sql) yuklu olarak acilir.
 * <p>
 * Bu canli durumun aynisidir: Laravel'in olusturdugu dolu bir veritabani. Flyway
 * V1'i calistirmaz, baseline olarak kabul eder. V1'in kendisi {@code BaselineSchemaIT}'de
 * bos bir veritabaninda dogrulanir.
 * <p>
 * Konteyner Spring context'iyle birlikte onbellekte tutulur; ayni context'i kullanan
 * test siniflari ayni veritabanini paylasir. Veri degistiren testler kendi
 * transaction'larinda calisip geri almali ({@code @Transactional}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String IMAGE = "postgres:17-alpine";
	static final String REFERENCE_DATA = "reference/reference-data.sql";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(IMAGE))
			.withDatabaseName("olivamore")
			// postgres imaji ilk aciliste bu klasordeki .sql dosyalarini psql ile calistirir
			.withCopyFileToContainer(MountableFile.forHostPath(REFERENCE_DATA), "/docker-entrypoint-initdb.d/01-reference-data.sql");
	}

}
