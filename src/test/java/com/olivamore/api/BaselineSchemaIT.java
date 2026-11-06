package com.olivamore.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway baseline'i Laravel semasiyla birebir ayni mi?
 * <p>
 * Referans veritabani (Laravel'in olusturdugu sema + veri) ile bos bir veritabanina
 * V1__baseline.sql uygulanmis hali karsilastirilir: kolonlar, constraint'ler, index'ler.
 * Kirilirsa V1 ya da reference/schema.sql birbirinden kopmus demektir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BaselineSchemaIT {

	private static final String COLUMNS = """
		select table_name || '.' || column_name || ' ' || data_type
		       || coalesce('(' || character_maximum_length || ')', '')
		       || coalesce(' p' || numeric_precision || ',' || numeric_scale, '')
		       || case when is_nullable = 'NO' then ' not null' else '' end
		       || coalesce(' default ' || column_default, '')
		from information_schema.columns
		where table_schema = 'public' and table_name <> 'flyway_schema_history'
		""";

	private static final String CONSTRAINTS = """
		select conrelid::regclass || ' ' || conname || ' ' || pg_get_constraintdef(oid)
		from pg_constraint
		where connamespace = 'public'::regnamespace and conrelid::regclass::text <> 'flyway_schema_history'
		""";

	private static final String INDEXES = """
		select indexdef from pg_indexes
		where schemaname = 'public' and tablename <> 'flyway_schema_history'
		""";

	@Autowired
	JdbcTemplate reference;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void referenceDataIsLoaded() {
		assertThat(reference.queryForObject("select count(*) from production_batches", Long.class)).isEqualTo(273);
		assertThat(reference.queryForObject("select count(*) from orders", Long.class)).isEqualTo(335);
		// Flyway mevcut semayi V1 olarak kabul etti (calistirmadi), sonra Java'nin kendi tablolarini ekledi
		assertThat(reference.queryForObject(
			"select type from flyway_schema_history where version = '1'", String.class)).isEqualTo("BASELINE");
		assertThat(reference.queryForObject(
			"select success from flyway_schema_history where version = '2'", Boolean.class)).isTrue();
	}

	@Test
	void baselineMatchesLaravelSchema() {
		reference.execute("drop database if exists baseline_check");
		reference.execute("create database baseline_check");
		DataSource empty = new DriverManagerDataSource(
			postgres.getJdbcUrl().replace("/olivamore", "/baseline_check"), postgres.getUsername(), postgres.getPassword());

		Flyway.configure().dataSource(empty).locations("classpath:db/migration").load().migrate();
		JdbcTemplate baseline = new JdbcTemplate(empty);

		for (String query : List.of(COLUMNS, CONSTRAINTS, INDEXES)) {
			Set<String> expected = new TreeSet<>(reference.queryForList(query, String.class));
			Set<String> actual = new TreeSet<>(baseline.queryForList(query, String.class));
			assertThat(actual).as(query).isNotEmpty().containsExactlyElementsOf(expected);
		}
		// Laravel'in 73 tablosu (Java'nin kendi tablolari haric: flyway_schema_history, event_publication)
		assertThat(baseline.queryForObject("select count(*) from information_schema.tables where table_schema = 'public'"
			+ " and table_name not in ('flyway_schema_history', 'event_publication')", Long.class)).isEqualTo(73);
	}

}
