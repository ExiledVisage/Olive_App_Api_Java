package com.olivamore.api;

import com.olivamore.api.devdata.DemoDataSeeder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Uygulama Testcontainers veritabaniyla; veritabani bossa bugun biten demo veri yuklenir. */
public class TestOliveApiApplication {

	public static void main(String[] args) {
		WebServerApplicationContext context = (WebServerApplicationContext) SpringApplication
			.from(OliveApiApplication::main).with(TestcontainersConfiguration.class).run(args).getApplicationContext();
		DemoDataSeeder seeder = context.getBean(DemoDataSeeder.class);
		if (seeder.isEmpty()) {
			seeder.seed(LocalDate.now(ZoneOffset.UTC), DemoDataSeeder.SEED, context.getWebServer().getPort());
		}
	}

}
