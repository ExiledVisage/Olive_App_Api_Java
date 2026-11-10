package com.olivamore.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sifirdan kurulan veritabani ile Laravel veritabanindan yukseltilen veritabani ayni yere geliyor mu?
 * <ul>
 *   <li><b>uygulama</b>: bos Postgres + butun migration'lar + seed ({@link TestcontainersConfiguration})</li>
 *   <li><b>laravel</b>: ayni konteynerde ayri bir veritabani: Laravel'in tam dokumu
 *       ({@code reference/reference-data.sql}), ustune canli gecisteki gibi baseline 1 ve V2 sonrasi</li>
 * </ul>
 * Sema (kolon, constraint, index), her tablonun satir sayisi ve sequence degerleri
 * karsilastirilir. Kirilirsa migration'lar, seed ya da Laravel semasi birbirinden kopmus demektir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaselineSchemaIT {

	/** Flyway'in kaydi iki kurulumda bilerek farkli (biri baseline'li). */
	private static final String JAVA_TABLES = "('flyway_schema_history')";

	private static final String COLUMNS = """
		select table_name || '.' || column_name || ' ' || data_type
		       || coalesce('(' || character_maximum_length || ')', '')
		       || coalesce(' p' || numeric_precision || ',' || numeric_scale, '')
		       || case when is_nullable = 'NO' then ' not null' else '' end
		       || coalesce(' default ' || column_default, '')
		from information_schema.columns
		where table_schema = 'public' and table_name not in %s
		""".formatted(JAVA_TABLES);

	private static final String CONSTRAINTS = """
		select conrelid::regclass || ' ' || conname || ' ' || pg_get_constraintdef(oid)
		from pg_constraint
		where connamespace = 'public'::regnamespace and conrelid::regclass::text not in %s
		""".formatted(JAVA_TABLES);

	private static final String INDEXES = """
		select indexdef from pg_indexes
		where schemaname = 'public' and tablename not in %s
		""".formatted(JAVA_TABLES);

	private static final String TABLES = """
		select table_name from information_schema.tables
		where table_schema = 'public' and table_type = 'BASE TABLE' and table_name not in %s
		order by table_name
		""".formatted(JAVA_TABLES);

	@Autowired
	JdbcTemplate app;

	@Autowired
	PostgreSQLContainer postgres;

	JdbcTemplate laravel;

	@BeforeAll
	void loadLaravelReference() throws Exception {
		app.execute("drop database if exists laravel");
		app.execute("create database laravel");
		postgres.copyFileToContainer(MountableFile.forHostPath("reference/reference-data.sql"), "/tmp/reference-data.sql");
		Container.ExecResult result = postgres.execInContainer("psql", "-q", "-v", "ON_ERROR_STOP=1",
			"-U", postgres.getUsername(), "-d", "laravel", "-f", "/tmp/reference-data.sql");
		assertThat(result.getExitCode()).as(result.getStderr()).isZero();
		laravel = new JdbcTemplate(new DriverManagerDataSource(
			postgres.getJdbcUrl().replace("/oliveapp", "/laravel"), postgres.getUsername(), postgres.getPassword()));
		Flyway.configure().dataSource(laravel.getDataSource()).baselineOnMigrate(true).baselineVersion("1").load().migrate();
	}

	@Test
	void flywayBuiltTheSchemaFromScratch() {
		// Tek basina kurulum: Laravel'in 69 migration'i ve kayit tablosu (baseline degil), sonra V2-V5
		List<String> history = app.queryForList(
			"select version || ' ' || type || ' ' || success from flyway_schema_history order by installed_rank", String.class);
		assertThat(history).hasSize(74).allMatch(h -> h.endsWith(" SQL true"));
		assertThat(history.getFirst()).isEqualTo("0.00 SQL true");
		assertThat(history.get(69)).isEqualTo("0.69 SQL true");
		assertThat(history.get(70)).isEqualTo("2 SQL true");
		assertThat(history.getLast()).isEqualTo("5 SQL true");
	}

	@Test
	void schemaMatchesLaravel() {
		for (String query : List.of(COLUMNS, CONSTRAINTS, INDEXES)) {
			Set<String> expected = normalized(laravel.queryForList(query, String.class));
			Set<String> actual = normalized(app.queryForList(query, String.class));
			assertThat(actual).as(query).isNotEmpty().containsExactlyElementsOf(expected);
		}
		assertThat(app.queryForList(TABLES, String.class)).hasSize(67);
	}

	@Test
	void seedMatchesLaravelData() {
		List<String> tables = laravel.queryForList(TABLES, String.class);
		for (String table : tables) {
			assertThat(count(app, table)).as(table).isEqualTo(count(laravel, table));
		}
		assertThat(count(app, "production_batches")).isEqualTo(273);
		assertThat(count(app, "orders")).isEqualTo(335);
	}

	@Test
	void sequencesContinueAfterSeed() {
		String query = "select sequencename || '=' || coalesce(last_value, 0) from pg_sequences where schemaname = 'public'";
		assertThat(new TreeSet<>(app.queryForList(query, String.class)))
			.containsExactlyElementsOf(new TreeSet<>(laravel.queryForList(query, String.class)));
	}

	/**
	 * Laravel'in {@code check ("unit" in ('KG', 'LITRE'))} kisiti Postgres'te iki yazilisla saklanabiliyor:
	 * migration'la kurulunca {@code ANY ((ARRAY['KG'::character varying, ...])::text[])}, pg_dump'tan
	 * yuklenince {@code ANY (ARRAY[('KG'::character varying)::text, ...])}. Anlam ayni; ikincisi birinciye cevrilir.
	 */
	static Set<String> normalized(List<String> definitions) {
		Set<String> out = new TreeSet<>();
		for (String d : definitions) {
			out.add(d.replaceAll("\\('([^']*)'::character varying\\)::text", "'$1'::character varying")
				.replaceAll("ANY \\(ARRAY\\[([^\\]]*)\\]\\)", "ANY ((ARRAY[$1])::text[])"));
		}
		return out;
	}

	private static long count(JdbcTemplate jdbc, String table) {
		Long n = jdbc.queryForObject("select count(*) from public." + table, Long.class);
		return n == null ? 0 : n;
	}

}
