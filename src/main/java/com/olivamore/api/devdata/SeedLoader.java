package com.olivamore.api.devdata;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * {@code pg_dump --data-only} ciktisini JDBC ile yukler; psql gerekmez.
 * <p>
 * Dokumdeki {@code COPY ... FROM stdin} bloklari PostgreSQL'in COPY API'siyle
 * ({@link CopyManager}) oldugu gibi aktarilir: en hizli yol ve tirnak/kacis
 * sorunu yok. {@code setval} gibi diger komutlar normal calistirilir. psql
 * komutlari ({@code \restrict}) ve oturum ayarlari ({@code SET}, bos
 * {@code search_path}) atlanir: tablo adlari zaten {@code public.} onekli ve
 * baglanti havuza geri donunce bu ayarlar Hibernate'i bozardi.
 * <p>
 * Hepsi tek transaction'da: yarida kalirsa hicbir sey yazilmaz.
 */
public final class SeedLoader {

	/** Bos veritabani olcutu: kullanici yoksa veritabani bos sayilir. */
	static final String EMPTY_CHECK = "select not exists (select 1 from public.users)";

	private static final char BACKSLASH = '\\';

	private SeedLoader() {
	}

	public static boolean isEmpty(DataSource dataSource) throws SQLException {
		try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
			ResultSet rs = s.executeQuery(EMPTY_CHECK)) {
			rs.next();
			return rs.getBoolean(1);
		}
	}

	/** @return yuklenen satir sayisi (COPY ile) */
	public static long load(DataSource dataSource, Path dump) throws SQLException, IOException {
		long rows = 0;
		try (Connection c = dataSource.getConnection();
			BufferedReader in = Files.newBufferedReader(dump, StandardCharsets.UTF_8)) {
			boolean autoCommit = c.getAutoCommit();
			c.setAutoCommit(false);
			try {
				CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
				StringBuilder statement = new StringBuilder();
				String line;
				while ((line = in.readLine()) != null) {
					if (statement.isEmpty() && skip(line)) {
						continue;
					}
					if (statement.isEmpty() && line.startsWith("COPY ") && line.endsWith(" FROM stdin;")) {
						rows += copy.copyIn(line, new StringReader(copyData(in)));
						continue;
					}
					statement.append(line).append('\n');
					if (line.endsWith(";")) {
						try (Statement s = c.createStatement()) {
							s.execute(statement.toString());
						}
						statement.setLength(0);
					}
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
		return rows;
	}

	/** COPY verisi: bitis satirina ({@code \.}) kadar. */
	private static String copyData(BufferedReader in) throws IOException {
		String end = BACKSLASH + ".";
		StringBuilder data = new StringBuilder();
		String line;
		while ((line = in.readLine()) != null && !line.equals(end)) {
			data.append(line).append('\n');
		}
		return data.toString();
	}

	private static boolean skip(String line) {
		return line.isBlank()
			|| line.startsWith("--")
			|| line.charAt(0) == BACKSLASH
			|| line.startsWith("SET ")
			|| line.startsWith("SELECT pg_catalog.set_config(");
	}

}
