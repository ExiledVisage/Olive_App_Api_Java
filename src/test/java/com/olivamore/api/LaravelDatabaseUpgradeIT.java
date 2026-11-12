package com.olivamore.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Canli gecis senaryosu: Laravel'in kurdugu dolu veritabani (Flyway kaydi yok), uzerine baseline 1 ve V2 sonrasi.
 * Veritabani V0.xx ile kurulur, Flyway kaydi silinir, Laravel'in yazacagi satirlar eklenir, sonra yukseltilir.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LaravelDatabaseUpgradeIT {

	private static final String USER_MODEL = "App\\Models\\User";

	private final PostgreSQLContainer postgres =
		new PostgreSQLContainer(DockerImageName.parse(TestcontainersConfiguration.IMAGE)).withDatabaseName("upgrade");

	@BeforeAll
	void start() {
		postgres.start();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	@Test
	void movesRolesAndPermissionsWithoutLosingAnything() {
		Database db = laravelDatabase();
		long admin = db.user("admin@olivamore.com");
		long operator = db.user("uretim@olivamore.com");
		long adminRole = db.role("admin", "web");
		long operatorRole = db.role("production_operator", "sanctum");
		long view = db.permission("customer.view", "web");
		long edit = db.permission("customer.edit", "web");
		db.assignRole(adminRole, USER_MODEL, admin);
		db.assignRole(operatorRole, USER_MODEL, operator);
		db.assignPermission(edit, USER_MODEL, operator);
		db.grant(adminRole, view);
		db.grant(adminRole, edit);
		db.grant(operatorRole, view);

		db.upgrade();

		JdbcTemplate jdbc = db.jdbc;
		assertThat(jdbc.queryForList("select user_id || ':' || role_id from user_roles order by 1", String.class))
			.containsExactlyInAnyOrder(admin + ":" + adminRole, operator + ":" + operatorRole);
		assertThat(jdbc.queryForList("select user_id || ':' || permission_id from user_permissions", String.class))
			.containsExactly(operator + ":" + edit);
		long measure = jdbc.queryForObject("select id from permissions where name = 'storage_unit.measure'", Long.class);
		assertThat(jdbc.queryForList("select role_id || ':' || permission_id from role_permissions", String.class))
			.containsExactlyInAnyOrder(adminRole + ":" + view, adminRole + ":" + edit, operatorRole + ":" + view,
				adminRole + ":" + measure);
		assertThat(jdbc.queryForObject("select name from roles where id = ?", String.class, operatorRole))
			.isEqualTo("production_operator");
		assertThat(jdbc.queryForObject("select name from permissions where id = ?", String.class, edit))
			.isEqualTo("customer.edit");
		assertThat(jdbc.queryForList("select email from users order by id", String.class))
			.containsExactly("admin@olivamore.com", "uretim@olivamore.com");
		assertThat(jdbc.queryForObject("select password from users where id = ?", String.class, admin)).isEqualTo("hash");

		assertThat(db.tables()).doesNotContain("model_has_roles", "model_has_permissions", "role_has_permissions",
			"migrations", "cache", "cache_locks", "jobs", "job_batches", "failed_jobs", "sessions",
			"password_reset_tokens", "personal_access_tokens");
		assertThat(db.columns("roles")).doesNotContain("guard_name");
		assertThat(db.columns("permissions")).doesNotContain("guard_name");
		assertThat(db.columns("users")).doesNotContain("remember_token").contains("email_verified_at");
		assertThat(jdbc.queryForList("select version from flyway_schema_history where version is not null "
			+ "order by installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9",
			"10");
	}

	@Test
	void grantsTankMeasurementToAdminAndToWhoeverAdjustsStock() {
		Database db = laravelDatabase();
		long operator = db.user("uretim@olivamore.com");
		long adminRole = db.role("admin", "web");
		long warehouseRole = db.role("warehouse_operator", "web");
		long plannerRole = db.role("planner", "web");
		long adjustment = db.permission("storage_movement.adjustment", "web");
		db.grant(warehouseRole, adjustment);
		db.assignPermission(adjustment, USER_MODEL, operator);

		db.upgrade();

		JdbcTemplate jdbc = db.jdbc;
		long measure = jdbc.queryForObject("select id from permissions where name = 'storage_unit.measure'", Long.class);
		assertThat(jdbc.queryForList("select role_id from role_permissions where permission_id = ?", Long.class,
			measure)).containsExactlyInAnyOrder(adminRole, warehouseRole).doesNotContain(plannerRole);
		assertThat(jdbc.queryForList("select user_id from user_permissions where permission_id = ?", Long.class,
			measure)).containsExactly(operator);
	}

	@Test
	void marksTanksDescribedAsRestingTanksAsPreSettling() {
		Database db = laravelDatabase();
		long byName = db.tank("Gemlik Ön Dinlenme Tankı", "T-01", null);
		long byCode = db.tank("Tank 2", "DİNLENME-02", null);
		long byDescription = db.tank("Tank 3", "T-03", "dinlenme icin kullaniliyor");
		long last = db.tank("Son Tank", "SON-01", "blend");
		String mixing = "update storage_units set is_blend_tank = true, allow_multiple_production_product_types = true,"
			+ " allow_multiple_production_products = true where id in (?, ?)";
		db.jdbc.update(mixing, byName, last);

		db.upgrade();

		assertThat(db.jdbc.queryForList("select id from storage_units where kind = 'pre_settling'", Long.class))
			.containsExactlyInAnyOrder(byName, byCode, byDescription);
		assertThat(db.jdbc.queryForObject("select kind from storage_units where id = ?", String.class, last))
			.isEqualTo("final");
		// on dinlendirme tanki tek urun tipiyle calisir; son tankin ayarlari kalir
		String flags = "select is_blend_tank or allow_multiple_production_product_types"
			+ " or allow_multiple_production_products from storage_units where id = ?";
		assertThat(db.jdbc.queryForObject(flags, Boolean.class, byName)).isFalse();
		assertThat(db.jdbc.queryForObject(flags, Boolean.class, last)).isTrue();
	}

	@Test
	void addsThePreSettlingStageExtensionToExistingTypes() {
		Database db = laravelDatabase();
		for (String key : List.of("weighing", "storage_movement", "material_transformation", "analysis")) {
			db.jdbc.update("insert into stage_extension_types (key, name) values (?, ?)", key, key);
		}

		db.upgrade();

		assertThat(db.jdbc.queryForList("select key from stage_extension_types order by id", String.class))
			.containsExactly("weighing", "storage_movement", "material_transformation", "analysis", "pre_settling");
	}

	@Test
	void emptyDatabaseGetsExtensionTypesFromTheSeed() {
		Database db = laravelDatabase();

		db.upgrade();

		assertThat(db.count("stage_extension_types")).isZero();
	}

	@Test
	void mergesActiveMaterialsOfAFinalTankIntoOneTankMaterialPerType() {
		Database db = laravelDatabase();
		long oil = db.product("olive_oil", null);
		long gemlik = db.type(oil, "Gemlik", "LITRE", "0.9000");
		long ayvalik = db.type(oil, "Ayvalik", "LITRE", "0.9000");
		long acidity = db.definition(oil, "acidity", "decimal");
		long cultivar = db.definition(oil, "cultivar", "string");
		long peroxide = db.definition(oil, "peroxide", "integer");
		long tank = db.tank("Son Tank", "SON-01", null);
		long single = db.tank("Son Tank 2", "SON-02", null);
		long resting = db.tank("Dinlenme Tanki", "DIN-01", null);
		long first = db.material(tank, gemlik, "100", "LITRE", "BATCH_PROCESS");
		long kilograms = db.material(tank, gemlik, "90", "KG", "BATCH_PROCESS");
		long opening = db.material(tank, gemlik, "50", "LITRE", "STORAGE_OPENING");
		long other = db.material(tank, ayvalik, "40", "LITRE", "BATCH_PROCESS");
		long alone = db.material(single, gemlik, "70", "LITRE", "BATCH_PROCESS");
		long settling = db.material(resting, gemlik, "30", "LITRE", "BATCH_PROCESS");
		db.decimal(first, acidity, "0.30");
		db.decimal(kilograms, acidity, "0.50");
		db.decimal(opening, acidity, "0.20");
		db.text(first, cultivar, "Gemlik");
		db.text(kilograms, cultivar, "Gemlik");
		db.text(opening, cultivar, "Gemlik");
		db.integer(first, peroxide, 10);
		db.integer(kilograms, peroxide, 13);
		db.decimal(alone, acidity, "0.40");
		db.text(alone, cultivar, "Edremit");
		db.balance(tank, oil, "300", "LITRE");
		db.balance(tank, oil, "90", "KG");

		db.upgrade();

		JdbcTemplate jdbc = db.jdbc;
		long mix = db.tankMaterial(tank, gemlik);
		// 90 kg / 0.9 = 100 L; (100 x 0.30 + 100 x 0.50 + 50 x 0.20) / 250 = 0.36; peroxide yalnizca ikisinde var
		assertThat(jdbc.queryForMap("select quantity, unit, status, production_batch_id from materials where id = ?", mix))
			.containsEntry("unit", "LITRE").containsEntry("status", "active").containsEntry("production_batch_id", null)
			.hasEntrySatisfying("quantity", q -> assertThat((BigDecimal) q).isEqualByComparingTo("250"));
		assertThat(db.decimalOf(mix, acidity)).isEqualByComparingTo("0.36");
		assertThat(db.textOf(mix, cultivar)).isEqualTo("Gemlik");
		assertThat(db.count("material_attribute_values where material_id = " + mix)).isEqualTo(2);
		assertThat(jdbc.queryForList("select source_material_id || ' ' || quantity || ' ' || unit || ' ' || movement_type"
			+ " || ' ' || transfer_reference from storage_movements where material_id = ? order by id", String.class, mix))
			.containsExactly(first + " 100.00 LITRE TRANSFER_IN BIRLESTIRME", kilograms + " 100.00 LITRE TRANSFER_IN "
				+ "BIRLESTIRME", opening + " 50.00 LITRE TRANSFER_IN BIRLESTIRME");
		assertThat(jdbc.queryForList("select status || ' ' || coalesce(current_location_type, '-') from materials"
			+ " where id in (?, ?, ?, ?, ?)", String.class, first, kilograms, opening, other, alone))
			.containsOnly("consumed -");

		long ayvalikMix = db.tankMaterial(tank, ayvalik);
		assertThat(jdbc.queryForObject("select quantity from materials where id = ?", BigDecimal.class, ayvalikMix))
			.isEqualByComparingTo("40");
		// tek materyalli grup da tank materyaline cevrilir, degerleri aynen gecer
		long singleMix = db.tankMaterial(single, gemlik);
		assertThat(db.decimalOf(singleMix, acidity)).isEqualByComparingTo("0.40");
		assertThat(db.textOf(singleMix, cultivar)).isEqualTo("Edremit");
		// on dinlendirme tankina dokunulmaz
		assertThat(jdbc.queryForObject("select status || ' ' || current_location_id from materials where id = ?",
			String.class, settling)).isEqualTo("active " + resting);
		assertThat(jdbc.queryForList("select current_quantity || ' ' || unit from storage_unit_balances"
			+ " where storage_unit_id = ?", String.class, tank)).containsExactly("290.00 LITRE");
	}

	@Test
	void movesOpenFillingsToTheTankMaterialAndLeavesFinishedOnesAlone() {
		Database db = laravelDatabase();
		long oil = db.product("olive_oil", null);
		long gemlik = db.type(oil, "Gemlik", "LITRE", "0.9000");
		long tank = db.tank("Son Tank", "SON-01", null);
		long litres = db.material(tank, gemlik, "100", "LITRE", "BATCH_PROCESS");
		long kilograms = db.material(tank, gemlik, "90", "KG", "BATCH_PROCESS");
		long planned = db.run(tank, "planned");
		long started = db.run(tank, "in_progress");
		long completed = db.run(tank, "completed");
		db.runMaterial(planned, litres, "30");
		db.runMaterial(planned, kilograms, "18");
		db.runMaterial(started, kilograms, "9");
		db.runMaterial(completed, litres, "10");

		db.upgrade();

		long mix = db.tankMaterial(tank, gemlik);
		String rows = "select source_material_id || ' ' || quantity_used from finished_product_run_materials"
			+ " where finished_product_run_id = ?";
		// 18 kg / 0.9 = 20 L, kosu basina toplanir
		assertThat(db.jdbc.queryForList(rows, String.class, planned)).containsExactly(mix + " 50.0000");
		assertThat(db.jdbc.queryForList(rows, String.class, started)).containsExactly(mix + " 10.0000");
		assertThat(db.jdbc.queryForList(rows, String.class, completed)).containsExactly(litres + " 10.0000");
	}

	@Test
	void stopsWhenAFinalTankHoldsUnitsThatCannotBeConverted() {
		Database db = laravelDatabase();
		long oil = db.product("olive_oil", null);
		long gemlik = db.type(oil, "Gemlik", "LITRE", null);
		long tank = db.tank("Son Tank", "SON-01", null);
		db.material(tank, gemlik, "100", "LITRE", "BATCH_PROCESS");
		db.material(tank, gemlik, "90", "KG", "BATCH_PROCESS");

		db.assertUpgradeFails("yogunlugu tanimsiz oldugu icin birimi cevrilemeyen 1 materyal");
		assertThat(db.count("materials where status = 'active' and origin_type <> 'TANK'")).isEqualTo(2);
		assertThat(db.count("materials where origin_type = 'TANK'")).isZero();
	}

	@Test
	void stopsWhenAnApprovalWaitsToMoveAMaterialThatWillBeMerged() {
		Database db = laravelDatabase();
		long oil = db.product("olive_oil", null);
		long gemlik = db.type(oil, "Gemlik", "LITRE", "0.9000");
		long tank = db.tank("Son Tank", "SON-01", null);
		long material = db.material(tank, gemlik, "100", "LITRE", "BATCH_PROCESS");
		db.awaitingStorageMovement("outbound", "{\"material_id\": " + material + ", \"quantity\": 10}");

		db.assertUpgradeFails("onay bekleyen 1 depo hareketi");
		assertThat(db.count("materials where origin_type = 'TANK'")).isZero();
	}

	@Test
	void convertsJsonColumnsAndKeepsTheirData() {
		Database db = laravelDatabase();
		long user = db.user("admin@olivamore.com");
		long report = db.jdbc.queryForObject("insert into analytics_reports (user_id, name, config) "
			+ "values (?, 'Rapor', '{\"b\": 1, \"a\": [1, {\"c\": \"ş\"}]}'::json) returning id", Long.class, user);

		db.upgrade();

		assertThat(db.jdbc.queryForObject("select config = '{\"a\": [1, {\"c\": \"ş\"}], \"b\": 1}'::jsonb "
			+ "from analytics_reports where id = ?", Boolean.class, report)).isTrue();
		assertThat(db.jdbc.queryForList("select table_name || '.' || column_name from information_schema.columns "
			+ "where table_schema = 'public' and data_type = 'json'", String.class)).isEmpty();
		assertThat(db.jdbc.queryForObject("select is_nullable from information_schema.columns "
			+ "where table_name = 'analytics_reports' and column_name = 'config'", String.class)).isEqualTo("NO");
	}

	@Test
	void stopsOnAssignmentOfAnotherModel() {
		Database db = laravelDatabase();
		long role = db.role("admin", "web");
		db.assignRole(role, "App\\Models\\Customer", db.user("admin@olivamore.com"));

		db.assertUpgradeFails("kullanici olmayan");
		assertThat(db.count("model_has_roles")).isEqualTo(1);
	}

	@Test
	void stopsOnAssignmentOfMissingUser() {
		Database db = laravelDatabase();
		db.assignPermission(db.permission("customer.view", "web"), USER_MODEL, 999_999);

		db.assertUpgradeFails("var olmayan kullaniciya");
		assertThat(db.count("model_has_permissions")).isEqualTo(1);
	}

	@Test
	void stopsOnRoleNameUsedByTwoGuards() {
		Database db = laravelDatabase();
		db.role("operator", "web");
		db.role("operator", "sanctum");

		db.assertUpgradeFails("ayni adi tasiyan 1 rol");
		assertThat(db.columns("roles")).contains("guard_name");
	}

	@Test
	void stopsOnPermissionNameUsedByTwoGuards() {
		Database db = laravelDatabase();
		db.permission("customer.view", "web");
		db.permission("customer.view", "sanctum");

		db.assertUpgradeFails("ayni adi tasiyan 1 izin");
		assertThat(db.columns("permissions")).contains("guard_name");
	}

	/** Laravel'in biraktigi durum: tablolar var, Flyway kaydi yok. */
	private Database laravelDatabase() {
		String name = "laravel_" + UUID.randomUUID().toString().replace("-", "");
		new JdbcTemplate(dataSource(postgres.getJdbcUrl())).execute("create database " + name);
		Database db = new Database(dataSource(postgres.getJdbcUrl().replace("/upgrade", "/" + name)));
		Flyway.configure().dataSource(db.source).target("0.69").load().migrate();
		db.jdbc.execute("drop table flyway_schema_history");
		return db;
	}

	private DriverManagerDataSource dataSource(String url) {
		return new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
	}

	private static final class Database {

		final DriverManagerDataSource source;
		final JdbcTemplate jdbc;

		Database(DriverManagerDataSource source) {
			this.source = source;
			this.jdbc = new JdbcTemplate(source);
		}

		void upgrade() {
			Flyway.configure().dataSource(source).baselineOnMigrate(true).baselineVersion("1").load().migrate();
		}

		void assertUpgradeFails(String message) {
			assertThatThrownBy(this::upgrade).isInstanceOf(FlywayException.class).hasMessageContaining(message);
		}

		long user(String email) {
			return jdbc.queryForObject("insert into users (name, email, password, remember_token) values (?, ?, 'hash', 't') "
				+ "returning id", Long.class, email, email);
		}

		long tank(String name, String code, String description) {
			return jdbc.queryForObject("insert into storage_units (name, code, capacity, capacity_unit, description) "
				+ "values (?, ?, 1000, 'LITRE', ?) returning id", Long.class, name, code, description);
		}

		long product(String key, String defaultDensity) {
			return jdbc.queryForObject("insert into production_products (key, name, default_density_kg_per_litre) "
				+ "values (?, ?, ?) returning id", Long.class, key, key, decimal(defaultDensity));
		}

		long type(long product, String name, String unit, String density) {
			return jdbc.queryForObject("insert into production_product_types (production_product_id, name, code, unit, "
				+ "density_kg_per_litre) values (?, ?, ?, ?, ?) returning id", Long.class, product, name, name, unit,
				decimal(density));
		}

		/** Urun-attribute eslemesi. */
		long definition(long product, String key, String dataType) {
			long attribute = jdbc.queryForObject("insert into attribute_definitions (attribute_key, label, data_type) "
				+ "values (?, ?, ?) returning id", Long.class, key, key, dataType);
			return jdbc.queryForObject("insert into production_product_attribute_definitions (production_product_id, "
				+ "attribute_definition_id) values (?, ?) returning id", Long.class, product, attribute);
		}

		/** Laravel'in biraktigi gibi tankta duran aktif materyal. */
		long material(long tank, long type, String quantity, String unit, String origin) {
			return jdbc.queryForObject("insert into materials (production_product_type_id, origin_type, quantity, unit, "
				+ "status, current_location_type, current_location_id) values (?, ?, ?, ?, 'active', 'storage_unit', ?) "
				+ "returning id", Long.class, type, origin, new BigDecimal(quantity), unit, tank);
		}

		void decimal(long material, long definition, String value) {
			jdbc.update("insert into material_attribute_values (material_id, production_product_attribute_definition_id, "
				+ "value_decimal) values (?, ?, ?)", material, definition, new BigDecimal(value));
		}

		void integer(long material, long definition, long value) {
			jdbc.update("insert into material_attribute_values (material_id, production_product_attribute_definition_id, "
				+ "value_integer) values (?, ?, ?)", material, definition, value);
		}

		void text(long material, long definition, String value) {
			jdbc.update("insert into material_attribute_values (material_id, production_product_attribute_definition_id, "
				+ "value_text) values (?, ?, ?)", material, definition, value);
		}

		void balance(long tank, long product, String quantity, String unit) {
			jdbc.update("insert into storage_unit_balances (storage_unit_id, production_product_id, current_quantity, "
				+ "unit) values (?, ?, ?, ?)", tank, product, new BigDecimal(quantity), unit);
		}

		long run(long tank, String status) {
			long recipe = jdbc.queryForObject("insert into finished_product_recipes (name) values ('Recete') returning id",
				Long.class);
			long product = jdbc.queryForObject("insert into finished_products (finished_product_recipe_id, name, "
				+ "volume_litre) values (?, 'Sise', 1) returning id", Long.class, recipe);
			return jdbc.queryForObject("insert into finished_product_runs (finished_product_id, finished_product_recipe_id, "
				+ "quantity, status, source_storage_unit_id) values (?, ?, 10, ?, ?) returning id", Long.class, product,
				recipe, status, tank);
		}

		void runMaterial(long run, long material, String quantity) {
			jdbc.update("insert into finished_product_run_materials (finished_product_run_id, source_material_id, "
				+ "quantity_used) values (?, ?, ?)", run, material, new BigDecimal(quantity));
		}

		/** Asamasi depo hareketi uzantili (tur sabit), onay bekleyen parti islemi. */
		void awaitingStorageMovement(String action, String operationData) {
			long line = jdbc.queryForObject("insert into production_lines (name) values ('Hat') returning id", Long.class);
			long stage = jdbc.queryForObject("insert into production_stages (production_line_id, name, stage_order) "
				+ "values (?, 'Toplama', 1) returning id", Long.class, line);
			long batch = jdbc.queryForObject("insert into production_batches (batch_code, production_line_id) "
				+ "values ('P-1', ?) returning id", Long.class, line);
			long type = jdbc.queryForObject("insert into stage_extension_types (key, name) "
				+ "values ('storage_movement', 'Depo hareketi') returning id", Long.class);
			long field = jdbc.queryForObject("insert into stage_extension_type_fields (stage_extension_type_id, field_key, "
				+ "label, data_type) values (?, 'action', 'Hareket', 'string') returning id", Long.class, type);
			long extension = jdbc.queryForObject("insert into production_stage_extensions (production_stage_id, "
				+ "stage_extension_type_id) values (?, ?) returning id", Long.class, stage, type);
			jdbc.update("insert into production_stage_extension_values (production_stage_extension_id, "
				+ "stage_extension_type_field_id, value_text) values (?, ?, ?)", extension, field, action);
			jdbc.update("insert into batch_processes (production_batch_id, production_stage_id, status, operation_data) "
				+ "values (?, ?, 'awaiting_approval', ?::json)", batch, stage, operationData);
		}

		long tankMaterial(long tank, long type) {
			return jdbc.queryForObject("select id from materials where origin_type = 'TANK' and status = 'active' "
				+ "and current_location_id = ? and production_product_type_id = ?", Long.class, tank, type);
		}

		BigDecimal decimalOf(long material, long definition) {
			return jdbc.queryForObject("select value_decimal from material_attribute_values where material_id = ? "
				+ "and production_product_attribute_definition_id = ?", BigDecimal.class, material, definition);
		}

		String textOf(long material, long definition) {
			return jdbc.queryForObject("select value_text from material_attribute_values where material_id = ? "
				+ "and production_product_attribute_definition_id = ?", String.class, material, definition);
		}

		private static BigDecimal decimal(String value) {
			return value == null ? null : new BigDecimal(value);
		}

		long role(String name, String guard) {
			return jdbc.queryForObject("insert into roles (name, guard_name) values (?, ?) returning id", Long.class, name, guard);
		}

		long permission(String name, String guard) {
			return jdbc.queryForObject("insert into permissions (name, guard_name) values (?, ?) returning id", Long.class,
				name, guard);
		}

		void assignRole(long role, String model, long id) {
			jdbc.update("insert into model_has_roles (role_id, model_type, model_id) values (?, ?, ?)", role, model, id);
		}

		void assignPermission(long permission, String model, long id) {
			jdbc.update("insert into model_has_permissions (permission_id, model_type, model_id) values (?, ?, ?)",
				permission, model, id);
		}

		void grant(long role, long permission) {
			jdbc.update("insert into role_has_permissions (permission_id, role_id) values (?, ?)", permission, role);
		}

		long count(String table) {
			return jdbc.queryForObject("select count(*) from " + table, Long.class);
		}

		List<String> tables() {
			return jdbc.queryForList("select table_name from information_schema.tables where table_schema = 'public'", String.class);
		}

		List<String> columns(String table) {
			return jdbc.queryForList("select column_name from information_schema.columns where table_schema = 'public' "
				+ "and table_name = ?", String.class, table);
		}

	}

}
