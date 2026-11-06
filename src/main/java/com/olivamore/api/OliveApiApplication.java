package com.olivamore.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class OliveApiApplication {

	public static void main(String[] args) {
		// Laravel'in saat dilimi (config/app.php: UTC). Veritabanindaki timestamp kolonlari
		// saat dilimi olmadan UTC duvar saati tutuyor; JVM'in yerel saati karismasin.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(OliveApiApplication.class, args);
	}

}
