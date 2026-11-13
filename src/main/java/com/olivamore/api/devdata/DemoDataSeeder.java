package com.olivamore.api.devdata;

import com.olivamore.api.shared.time.Timestamps;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bos veritabanina demo fabrika: once tanim verisi ({@code devdata/master-data.sql}), sonra bitis gunune kadar bir
 * yillik akis ({@link DemoFactory}) calisan uygulamanin API'si uzerinden. Ayni bitis gunu ve tohum ayni veriyi uretir.
 */
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

	/** Demo verinin varsayilan tohumu. */
	public static final long SEED = 2026;

	/** Bos veritabani olcutu: kullanici yoksa veritabani bos sayilir. */
	static final String EMPTY_CHECK = "select not exists (select 1 from users)";

	private final DataSource dataSource;
	private final JdbcTemplate jdbc;

	public boolean isEmpty() {
		return Boolean.TRUE.equals(jdbc.queryForObject(EMPTY_CHECK, Boolean.class));
	}

	/**
	 * @param lastDay akisin son gunu (o gun 15:30'a kadar)
	 * @param port    calisan uygulamanin HTTP portu
	 */
	public void seed(LocalDate lastDay, long seed, int port) {
		LocalDate firstDay = lastDay.minusYears(1);
		try {
			loadMasterData(firstDay.atTime(7, 0));
			new DemoFactory(new DemoApi(port), jdbc, seed, firstDay, lastDay).run();
			jdbc.update("delete from refresh_tokens");
			jdbc.update("delete from auth_sessions");
		}
		finally {
			Timestamps.useClock(Clock.systemUTC());
		}
	}

	private void loadMasterData(LocalDateTime createdAt) {
		new ResourceDatabasePopulator(new ClassPathResource("devdata/master-data.sql")).execute(dataSource);
		List<String> tables = jdbc.queryForList("""
			select table_name from information_schema.columns
			where table_schema = 'public' and column_name in ('created_at', 'updated_at')
			group by table_name having count(*) = 2 order by table_name
			""", String.class);
		for (String table : tables) {
			jdbc.update("update " + table + " set created_at = ?, updated_at = ?", createdAt, createdAt);
		}
		jdbc.update("update users set email_verified_at = ?", createdAt);
		jdbc.queryForList("""
			select format('select setval(%L, (select max(%I) from %I))', pg_get_serial_sequence(table_name, column_name),
				column_name, table_name)
			from information_schema.columns
			where table_schema = 'public' and pg_get_serial_sequence(table_name, column_name) is not null
			""", String.class).forEach(sql -> jdbc.queryForList(sql));
	}

}
