package com.olivamore.api.support;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.devdata.SeedLoader;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

/**
 * Her test sinifindan once veritabanini demo verinin ilk haline dondurur: butun tablolar bosaltilir (sayaclar
 * sifirlanir), seed yeniden yuklenir. Boylece siniflar ayni Spring context'ini ve konteyneri paylasir ama
 * birbirinin verisini gormez. Bellekteki durum da sifirlanir: rate limit kovalari ve uygulama saati.
 */
public class FreshDatabaseListener extends AbstractTestExecutionListener {

	@Override
	public int getOrder() {
		return 1000;
	}

	@Override
	public void beforeTestClass(TestContext testContext) throws Exception {
		ApplicationContext context = testContext.getApplicationContext();
		DataSource dataSource = context.getBean(DataSource.class);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		List<String> tables = jdbc.queryForList("""
			select format('%I.%I', schemaname, tablename) from pg_tables
			where schemaname = 'public' and tablename <> 'flyway_schema_history'
			""", String.class);
		if (!tables.isEmpty()) {
			jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
		}
		SeedLoader.load(dataSource, TestcontainersConfiguration.SEED_DATA);
		context.getBeanProvider(RateLimitBuckets.class).ifAvailable(RateLimitBuckets::clear);
		Timestamps.useClock(Clock.systemUTC());
	}

}
