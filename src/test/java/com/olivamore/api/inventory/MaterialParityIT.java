package com.olivamore.api.inventory;

import com.olivamore.api.TestcontainersConfiguration;
import com.olivamore.api.support.LaravelReplay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laravel'den yakalanan 30 materyal cevabi (src/test/resources/laravel/material.json,
 * reference/capture_material.py): liste suzgecleri ve arama, detay (miktar dokumu, hareketler, dolum
 * kosulari) ve soy grafi (kosu, depo yerlestirmesi, siparis dugumleri, derinlikler).
 * Yalnizca okur; canli karsilastirmada butun materyallerin detay ve soy grafi da ayni (MIGRATION_ROADMAP).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaterialParityIT {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void replaysLaravelBehaviour() throws Exception {
		LaravelReplay.Result result = new LaravelReplay("/laravel/material.json", port, jdbc).run();
		assertThat(result.steps()).isEqualTo(30);
		assertThat(result.failures()).as(result.report()).isEmpty();
	}

}
