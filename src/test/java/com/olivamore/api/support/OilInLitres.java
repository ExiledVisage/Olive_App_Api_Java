package com.olivamore.api.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seed'de zeytinyagi kilo ile tutulur. Litre birimli tipin kurallarini (cevrim, karisik birimli tank, kapasite)
 * sinayan siniflar Gemlik (3) ve Ayvalik (4) zeytinyagini sinif boyunca litreye alir; her sinif temiz veritabaniyla
 * basladigi icin ({@link FreshDatabaseListener}) geri almaya gerek yoktur.
 */
public final class OilInLitres {

	private OilInLitres() {
	}

	public static void apply(JdbcTemplate jdbc) {
		jdbc.update("update production_product_types set unit = 'LITRE' where id in (3, 4)");
	}

}
