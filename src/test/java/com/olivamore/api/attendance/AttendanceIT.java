package com.olivamore.api.attendance;

import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giris-cikis (docs/ATTENDANCE.md): kisa omurlu QR, bekci okutmasi (giris/cikis, cift okutma, sure, imza), yetki,
 * iceridekiler, mesai raporu (fazla mesai, yuvarlama, devamsizlik, gec kalma, mola), kisiye ozel plan, elle kayit
 * ve iptal.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceIT {

	static final String WORKER = "uretim@olivamore.com";
	static final String OFFICE = "depo@olivamore.com";
	static final String GUARD = "bekci@olivamore.com";
	static final long OFFICE_ID = 6;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	Api api;

	Instant clock;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@AfterEach
	void resetClock() {
		Timestamps.useClock(Clock.systemUTC());
	}

	@Test
	void guardScansTheWorkersCodeInThenOut() {
		at(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		JsonNode code = data(api.as(WORKER).get("/api/attendance/my-code"));
		assertThat(code.get("inside").asBoolean()).isFalse();
		assertThat(code.get("ttl_seconds").asInt()).isEqualTo(60);

		JsonNode in = data(scan(code.get("code").asString()));
		assertThat(in.get("kind").asString()).isEqualTo("in");
		assertThat(in.get("duplicate").asBoolean()).isFalse();
		assertThat(in.get("user").get("name").asString()).isEqualTo("Üretim İşçisi");

		// Ayni kod hemen tekrar: cift okutma, yeni kayit yok
		JsonNode again = data(scan(code.get("code").asString()));
		assertThat(again.get("duplicate").asBoolean()).isTrue();
		assertThat(again.get("kind").asString()).isEqualTo("in");

		assertThat(data(api.as(WORKER).get("/api/attendance/my-code")).get("inside").asBoolean()).isTrue();
		assertThat(data(api.as(GUARD).get("/api/attendance/inside")))
			.extracting(i -> i.get("user").get("name").asString()).contains("Üretim İşçisi");

		advance(3 * 3600);
		JsonNode out = data(scan(data(api.as(WORKER).get("/api/attendance/my-code")).get("code").asString()));
		assertThat(out.get("kind").asString()).isEqualTo("out");
		assertThat(data(api.as(GUARD).get("/api/attendance/inside")))
			.extracting(i -> i.get("user").get("name").asString()).doesNotContain("Üretim İşçisi");
	}

	@Test
	void expiredTamperedAndForeignCodesAreRejected() {
		at(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		String code = data(api.as(WORKER).get("/api/attendance/my-code")).get("code").asString();

		assertThat(api.as(WORKER).post("/api/attendance/scan", Map.of("code", code)).status()).isEqualTo(403);
		assertThat(scan("https://example.com/x").status()).isEqualTo(422);

		String[] parts = code.split("\\.");
		String forged = String.join(".", parts[0], "1", parts[2], parts[3], parts[4]);
		Response tampered = scan(forged);
		assertThat(tampered.status()).isEqualTo(422);
		assertThat(tampered.body()).contains("geçersiz");

		advance(61);
		Response expired = scan(code);
		assertThat(expired.status()).isEqualTo(422);
		assertThat(expired.body()).contains("süresi dolmuş");
	}

	@Test
	void reportCountsOvertimeLatenessAndAbsence() {
		// 2025-03-03 Pazartesi, plan 10:00-18:00 (480 dk, mola yok); Istanbul UTC+3
		event(OFFICE_ID, "in", "2025-03-03T06:45:00");
		event(OFFICE_ID, "out", "2025-03-03T15:40:00");
		// Sali gec gelip erken cikti
		event(OFFICE_ID, "in", "2025-03-04T07:20:00");
		event(OFFICE_ID, "out", "2025-03-04T14:00:00");
		// Persembe girdi, cikmadi
		event(OFFICE_ID, "in", "2025-03-06T07:00:00");
		// Cumartesi plansiz gun: hepsi fazla mesai
		event(OFFICE_ID, "in", "2025-03-08T07:00:00");
		event(OFFICE_ID, "out", "2025-03-08T09:10:00");

		JsonNode report = data(api.get("/api/attendance/report?from=2025-03-03&to=2025-03-09&user_id=" + OFFICE_ID));
		assertThat(report.get("rounding_minutes").asInt()).isEqualTo(15);
		JsonNode user = report.get("users").get(0);
		List<JsonNode> days = list(user.get("days"));

		JsonNode monday = days.get(0);
		assertThat(monday.get("worked_minutes").asLong()).isEqualTo(535);
		assertThat(monday.get("planned_minutes").asLong()).isEqualTo(480);
		assertThat(monday.get("overtime_minutes").asLong()).isEqualTo(45);
		assertThat(monday.get("late_minutes").asLong()).isZero();

		JsonNode tuesday = days.get(1);
		assertThat(tuesday.get("late_minutes").asLong()).isEqualTo(20);
		assertThat(tuesday.get("missing_minutes").asLong()).isEqualTo(80);
		assertThat(tuesday.get("overtime_minutes").asLong()).isZero();

		assertThat(days.get(2).get("absent").asBoolean()).isTrue();
		assertThat(days.get(2).get("missing_minutes").asLong()).isEqualTo(480);
		assertThat(days.get(3).get("missing_checkout").asBoolean()).isTrue();
		assertThat(days.get(5).get("planned_minutes").asLong()).isZero();
		assertThat(days.get(5).get("overtime_minutes").asLong()).isEqualTo(120);

		JsonNode totals = user.get("totals");
		assertThat(totals.get("overtime_minutes").asLong()).isEqualTo(165);
		assertThat(totals.get("absent_days").asInt()).isEqualTo(2);
		assertThat(totals.get("missing_checkouts").asInt()).isEqualTo(1);
		assertThat(totals.get("worked_days").asInt()).isEqualTo(4);
	}

	@Test
	void personalScheduleDeductsTheAssignedBreakUnlessTakenByScanning() {
		Response saved = api.put("/api/attendance/schedules", Map.of("user_id", OFFICE_ID, "days",
			List.of(Map.of("weekday", 1, "starts_at", "10:00", "ends_at", "18:00", "break_minutes", 60))));
		assertThat(saved.status()).as(saved.body()).isEqualTo(200);
		assertThat(data(saved).get("personal")).hasSize(1);

		// 2025-04-07 Pazartesi: 10:00-18:00, mola okutulmadi -> 60 dk dusulur
		event(OFFICE_ID, "in", "2025-04-07T07:00:00");
		event(OFFICE_ID, "out", "2025-04-07T15:00:00");
		// 2025-04-14 Pazartesi: 12:00-12:40 okutarak cikti -> 40 dk verdi, 20 dk daha dusulur
		event(OFFICE_ID, "in", "2025-04-14T07:00:00");
		event(OFFICE_ID, "out", "2025-04-14T09:00:00");
		event(OFFICE_ID, "in", "2025-04-14T09:40:00");
		event(OFFICE_ID, "out", "2025-04-14T15:40:00");

		List<JsonNode> days = list(data(api.get("/api/attendance/report?from=2025-04-07&to=2025-04-14&user_id="
			+ OFFICE_ID)).get("users").get(0).get("days"));
		assertThat(days.get(0).get("planned_minutes").asLong()).isEqualTo(420);
		assertThat(days.get(0).get("worked_minutes").asLong()).isEqualTo(420);
		assertThat(days.get(1).get("planned_minutes").asLong()).as("Sali kisinin planinda yok").isZero();
		assertThat(days.get(7).get("break_minutes").asLong()).isEqualTo(60);
		assertThat(days.get(7).get("worked_minutes").asLong()).isEqualTo(460);
		assertThat(days.get(7).get("overtime_minutes").asLong()).isEqualTo(30);

		assertThat(data(api.delete("/api/attendance/schedules/users/" + OFFICE_ID)).get("personal").isNull())
			.isTrue();
		Response invalid = api.put("/api/attendance/schedules", Map.of("days",
			List.of(Map.of("weekday", 1, "starts_at", "18:00", "ends_at", "10:00"))));
		assertThat(invalid.status()).isEqualTo(422);
	}

	@Test
	void managerRecordsAndVoidsWithAReason() {
		Response noNote = api.post("/api/attendance/events", Map.of("user_id", OFFICE_ID, "kind", "out",
			"occurred_at", "2025-05-05T15:00:00"));
		assertThat(noNote.status()).isEqualTo(422);
		assertThat(noNote.json().get("errors").has("note")).isTrue();

		Response created = api.post("/api/attendance/events", Map.of("user_id", OFFICE_ID, "kind", "out",
			"occurred_at", "2025-05-05T15:00:00", "note", "Çıkış okutulmamış"));
		assertThat(created.status()).as(created.body()).isEqualTo(201);
		long id = data(created).get("id").asLong();
		assertThat(data(created).get("source").asString()).isEqualTo("manual");
		assertThat(data(created).get("recorded_by").get("name").asString()).isEqualTo("Olivamore Admin");

		assertThat(api.post("/api/attendance/events/" + id + "/void", Map.of()).status()).isEqualTo(422);
		JsonNode voided = data(api.post("/api/attendance/events/" + id + "/void", Map.of("reason", "Yanlış gün")));
		assertThat(voided.get("voided_at").isNull()).isFalse();
		assertThat(api.post("/api/attendance/events/" + id + "/void", Map.of("reason", "x")).status()).isEqualTo(422);

		assertThat(data(api.get("/api/attendance/events?from=2025-05-05&to=2025-05-05&user_id=" + OFFICE_ID)))
			.hasSize(1);
		assertThat(data(api.get("/api/attendance/people"))).extracting(u -> u.get("name").asString())
			.contains("Kapı Görevlisi", "Depo ve Sipariş");
		assertThat(api.as(OFFICE).get("/api/attendance/people").status()).isEqualTo(403);
		assertThat(api.as(OFFICE).post("/api/attendance/events", Map.of()).status()).isEqualTo(403);
		assertThat(api.as(GUARD).get("/api/attendance/report?from=2025-05-05&to=2025-05-05").status())
			.isEqualTo(403);
	}

	private Response scan(String code) {
		return api.as(GUARD).post("/api/attendance/scan", Map.of("code", code, "device_label", "Kapı tableti"));
	}

	private void event(long userId, String kind, String utc) {
		jdbc.update("""
			insert into attendance_events (user_id, kind, occurred_at, source, created_at, updated_at)
			values (?, ?, ?, 'manual', now(), now())
			""", userId, kind, LocalDateTime.parse(utc));
	}

	private static List<JsonNode> list(JsonNode array) {
		return java.util.stream.StreamSupport.stream(array.spliterator(), false).toList();
	}

	private void at(Instant instant) {
		clock = instant;
		Timestamps.useClock(Clock.fixed(clock, ZoneOffset.UTC));
	}

	private void advance(long seconds) {
		at(clock.plusSeconds(seconds));
	}

}
