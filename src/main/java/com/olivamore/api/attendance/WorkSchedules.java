package com.olivamore.api.attendance;

import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Haftalik calisma plani (docs/ATTENDANCE.md): fabrika geneli satirlar ({@code user_id} bos) ve kisiye ozel plan.
 * Kisinin kendi satirlari varsa plani yalniz onlardan olusur; satiri olmayan gun izin.
 */
@Service
class WorkSchedules {

	/** @param weekday 1 Pazartesi ... 7 Pazar */
	record Day(int weekday, LocalTime startsAt, LocalTime endsAt, int breakMinutes) {

		/** Mola dusulmus planli sure. */
		long plannedMinutes() {
			return Math.max(0, Duration.between(startsAt, endsAt).toMinutes() - breakMinutes);
		}

	}

	/** @param personal kisiye ozel plan; yoksa null (fabrika geneli gecerli) */
	record Plan(Long userId, List<Day> factory, List<Day> personal) {
	}

	record DayRequest(Integer weekday, String startsAt, String endsAt, Integer breakMinutes) {
	}

	/** @param userId bos ise fabrika geneli plan degisir */
	record PlanRequest(Long userId, List<DayRequest> days) {
	}

	private record ScheduleLine(Long userId, int weekday, LocalTime startsAt, LocalTime endsAt, int breakMinutes) {
	}

	private final JdbcClient jdbc;

	WorkSchedules(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	Plan plan(Long userId) {
		List<Day> factory = days(null);
		List<Day> personal = userId == null ? null : days(userId);
		return new Plan(userId, factory, personal == null || personal.isEmpty() ? null : personal);
	}

	/** Verilen kapsamin (fabrika ya da kisi) satirlarini tamamen degistirir. */
	@Transactional
	Plan replace(PlanRequest request) {
		List<DayRequest> days = request.days() == null ? List.of() : request.days();
		if (request.userId() != null) {
			requireUser(request.userId());
		}
		List<Day> parsed = validate(days);
		delete(request.userId());
		LocalDateTime now = Timestamps.now();
		for (Day day : parsed) {
			jdbc.sql("""
				insert into work_schedules (user_id, weekday, starts_at, ends_at, break_minutes, created_at, updated_at)
				values (:user, :weekday, :starts, :ends, :break, :now, :now)
				""").param("user", request.userId()).param("weekday", day.weekday()).param("starts", day.startsAt())
				.param("ends", day.endsAt()).param("break", day.breakMinutes()).param("now", now).update();
		}
		return plan(request.userId());
	}

	/** Kisiye ozel plani kaldirir; kisi fabrika genelindeki plana doner. */
	@Transactional
	Plan clearPersonal(long userId) {
		requireUser(userId);
		delete(userId);
		return plan(userId);
	}

	/** Kisilerin gecerli planlari (gun -> plan) toplu: rapor icin. */
	@Transactional(readOnly = true)
	Map<Long, Map<Integer, Day>> effective(Collection<Long> userIds) {
		List<ScheduleLine> rows = jdbc.sql("""
			select user_id, weekday, starts_at, ends_at, break_minutes from work_schedules
			where user_id is null or user_id in (:users)
			""").param("users", userIds.isEmpty() ? List.of(-1L) : userIds).query(ScheduleLine.class).list();
		Map<Integer, Day> factory = new HashMap<>();
		Map<Long, Map<Integer, Day>> personal = new HashMap<>();
		for (ScheduleLine row : rows) {
			Day day = new Day(row.weekday(), row.startsAt(), row.endsAt(), row.breakMinutes());
			if (row.userId() == null) {
				factory.put(row.weekday(), day);
			} else {
				personal.computeIfAbsent(row.userId(), id -> new HashMap<>()).put(row.weekday(), day);
			}
		}
		Map<Long, Map<Integer, Day>> result = new HashMap<>();
		for (Long id : userIds) {
			result.put(id, personal.getOrDefault(id, factory));
		}
		return result;
	}

	/** Kisinin o gunku plani; izin gunuyse null. */
	Day dayOf(Map<Integer, Day> plan, LocalDate date) {
		return plan.get(date.getDayOfWeek().getValue());
	}

	private List<Day> days(Long userId) {
		return jdbc.sql("""
			select user_id, weekday, starts_at, ends_at, break_minutes from work_schedules
			where user_id is not distinct from :user order by weekday
			""").param("user", userId).query(ScheduleLine.class).list().stream()
			.map(r -> new Day(r.weekday(), r.startsAt(), r.endsAt(), r.breakMinutes())).toList();
	}

	private void delete(Long userId) {
		jdbc.sql("delete from work_schedules where user_id is not distinct from :user").param("user", userId)
			.update();
	}

	private void requireUser(long userId) {
		jdbc.sql("select id from users where id = :id").param("id", userId).query(Long.class).optional()
			.orElseThrow(() -> new ModelNotFoundException("User", userId));
	}

	private List<Day> validate(List<DayRequest> days) {
		ValidationErrors errors = new ValidationErrors();
		List<Day> parsed = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < days.size(); i++) {
			DayRequest day = days.get(i);
			String path = "days." + i + ".";
			if (day.weekday() == null || day.weekday() < 1 || day.weekday() > 7) {
				errors.add(path + "weekday", MessageKeys.IN);
				continue;
			}
			if (!seen.add(day.weekday())) {
				errors.add(path + "weekday", MessageKeys.DISTINCT);
				continue;
			}
			LocalTime starts = time(day.startsAt());
			LocalTime ends = time(day.endsAt());
			if (starts == null) {
				errors.add(path + "starts_at", MessageKeys.REQUIRED);
			}
			if (ends == null) {
				errors.add(path + "ends_at", MessageKeys.REQUIRED);
			}
			if (starts == null || ends == null) {
				continue;
			}
			if (!ends.isAfter(starts)) {
				errors.add(path + "ends_at", "Bitiş saati başlangıçtan sonra olmalıdır.");
				continue;
			}
			int breakMinutes = day.breakMinutes() == null ? 0 : day.breakMinutes();
			if (breakMinutes < 0 || breakMinutes >= Duration.between(starts, ends).toMinutes()) {
				errors.add(path + "break_minutes", "Mola süresi çalışma süresinden kısa olmalıdır.");
				continue;
			}
			parsed.add(new Day(day.weekday(), starts, ends, breakMinutes));
		}
		errors.throwIfAny();
		return parsed;
	}

	private static LocalTime time(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw ValidationFailedException.of("days", MessageKeys.DATE_FORMAT, "format", "HH:mm");
		}
	}

}
