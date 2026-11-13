package com.olivamore.api.support;

import com.olivamore.api.devdata.DemoDataSeeder;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Testlerin demo verisi: JVM'de bir kez, sabit bitis gunu ve tohumla {@link DemoDataSeeder} calisir (uygulamanin
 * API'si uzerinden), sonuc tablo tablo bellege alinir (COPY). Her test sinifindan once veritabani bosaltilip bu
 * kopya geri yuklenir; sequence'ler de ayni yere doner. Boylece her sinif ayni demo veriyle, hizlica baslar. Kopya
 * diske de yazilir ({@link DemoSnapshotCache}); uygulama kodu degismediyse sonraki calismalar seeder'i calistirmaz.
 */
public final class DemoSnapshot {

	/** Demo verinin son gunu: senaryolar bu gune gore kaydedildi. */
	public static final LocalDate LAST_DAY = LocalDate.of(2026, 11, 6);

	private static Map<String, byte[]> tables;
	private static Map<String, Sequence> sequences;

	private DemoSnapshot() {
	}

	/** Veritabanini demo verinin ilk haline getirir; ilk cagrida demo veriyi uretir. */
	public static synchronized void restore(ApplicationContext context) throws SQLException, IOException {
		DataSource dataSource = context.getBean(DataSource.class);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		truncate(jdbc);
		if (tables == null && !loadCached()) {
			Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
			if (port == null) {
				throw new IllegalStateException("Demo veri calisan uygulama ister (RANDOM_PORT)");
			}
			context.getBean(DemoDataSeeder.class).seed(LAST_DAY, DemoDataSeeder.SEED, port);
			capture(dataSource, jdbc);
			cache().write(new DemoSnapshotCache.Snapshot(tables, sequences));
			return;
		}
		try (Connection c = dataSource.getConnection()) {
			boolean autoCommit = c.getAutoCommit();
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("set local session_replication_role = replica");
				CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
				for (Map.Entry<String, byte[]> table : tables.entrySet()) {
					copy.copyIn("copy " + table.getKey() + " from stdin", new ByteArrayInputStream(table.getValue()));
				}
				for (Map.Entry<String, Sequence> sequence : sequences.entrySet()) {
					Sequence value = sequence.getValue();
					s.execute("select setval('" + sequence.getKey() + "', " + value.value() + ", " + value.called() + ")");
				}
				c.commit();
			}
			catch (SQLException | IOException | RuntimeException e) {
				c.rollback();
				throw e;
			}
			finally {
				c.setAutoCommit(autoCommit);
			}
		}
	}

	private static void capture(DataSource dataSource, JdbcTemplate jdbc) throws SQLException, IOException {
		Map<String, byte[]> captured = new LinkedHashMap<>();
		try (Connection c = dataSource.getConnection()) {
			CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
			for (String table : tableNames(jdbc)) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				copy.copyOut("copy " + table + " to stdout", out);
				captured.put(table, out.toByteArray());
			}
		}
		Map<String, Sequence> values = new LinkedHashMap<>();
		jdbc.query("select format('%I.%I', schemaname, sequencename) as name, last_value from pg_sequences "
			+ "where schemaname = 'public' order by 1", rs -> {
				long value = rs.getLong("last_value");
				boolean called = !rs.wasNull();
				values.put(rs.getString("name"), new Sequence(called ? value : 1, called));
			});
		tables = captured;
		sequences = values;
	}

	private static void truncate(JdbcTemplate jdbc) {
		List<String> tables = tableNames(jdbc);
		if (!tables.isEmpty()) {
			jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
		}
	}

	private static List<String> tableNames(JdbcTemplate jdbc) {
		return jdbc.queryForList("""
			select format('%I.%I', schemaname, tablename) from pg_tables
			where schemaname = 'public' and tablename <> 'flyway_schema_history' order by tablename
			""", String.class);
	}

	/** Diskteki kopya (ayni kod ve parametrelerle uretilmis) varsa onu alir. */
	private static boolean loadCached() throws IOException {
		Optional<DemoSnapshotCache.Snapshot> cached = cache().read();
		cached.ifPresent(snapshot -> {
			tables = snapshot.tables();
			sequences = snapshot.sequences();
		});
		return cached.isPresent();
	}

	private static DemoSnapshotCache cache() {
		return new DemoSnapshotCache(LAST_DAY, DemoDataSeeder.SEED);
	}

	record Sequence(long value, boolean called) {
	}

}
