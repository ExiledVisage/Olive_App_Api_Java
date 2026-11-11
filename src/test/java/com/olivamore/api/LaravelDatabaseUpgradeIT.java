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
			+ "order by installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
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
