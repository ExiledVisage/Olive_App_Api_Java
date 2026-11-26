package com.olivamore.api.attendance;

import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mesai raporu (docs/ATTENDANCE.md): giris-cikislar araliklara eslenir (cikis, girisin tarihine yazilir; gece
 * vardiyasi da tek gun), gunluk calisilan sure plana gore fazla mesai, eksik ve gec kalma olur.
 * <ul>
 * <li>calisilan = iceride gecen sure - (atanan mola - okutarak verilen mola, sifirdan kucukse sifir)</li>
 * <li>fazla mesai = calisilan - planli (erken gelme dahil her dakika), yuvarlama diliminde asagi; plansiz gunde
 * calisilanin tamami</li>
 * <li>cikisi olmayan giris: bugun ise "iceride" (su ana kadar sayilir), gecmis gunse "cikis yok" (sayilmaz)</li>
 * </ul>
 */
@Service
class AttendanceReport {

	record Day(LocalDate date, LocalDateTime firstIn, LocalDateTime lastOut, long presentMinutes,
		long breakMinutes, long workedMinutes, long plannedMinutes, long overtimeMinutes, long missingMinutes,
		long lateMinutes, boolean missingCheckout, boolean absent, boolean inside) {
	}

	record Totals(long workedMinutes, long plannedMinutes, long overtimeMinutes, long missingMinutes,
		int workedDays, int absentDays, int missingCheckouts) {
	}

	record UserReport(Attendance.User user, Totals totals, List<Day> days) {
	}

	record Report(LocalDate from, LocalDate to, int roundingMinutes, List<UserReport> users) {
	}

	private record EventLine(long userId, String userName, String kind, LocalDateTime occurredAt) {
	}

	private record Interval(LocalDateTime in, LocalDateTime out, boolean open) {
	}

	private final JdbcClient jdbc;
	private final WorkSchedules schedules;
	private final AttendanceSettings settings;

	AttendanceReport(JdbcClient jdbc, WorkSchedules schedules, AttendanceSettings settings) {
		this.jdbc = jdbc;
		this.schedules = schedules;
		this.settings = settings;
	}

	/**
	 * @param userId bos ise araligin herhangi bir gununde kaydi olan herkes
	 */
	@Transactional(readOnly = true)
	Report report(LocalDate from, LocalDate to, Long userId) {
		if (from == null || to == null) {
			throw ValidationFailedException.of(from == null ? "from" : "to", MessageKeys.REQUIRED);
		}
		if (to.isBefore(from)) {
			throw ValidationFailedException.of("to", "Bitiş tarihi başlangıçtan önce olamaz.");
		}
		if (from.plusDays(92).isBefore(to)) {
			throw ValidationFailedException.of("to", "Rapor en fazla 3 aylık olabilir.");
		}
		// Gece vardiyasinin cikisi ertesi gune duser; araligin bir gun sonrasina kadar okunur.
		List<EventLine> rows = jdbc.sql("""
			select e.user_id, u.name as user_name, e.kind, e.occurred_at
			from attendance_events e join users u on u.id = e.user_id
			where e.voided_at is null and (cast(:user as bigint) is null or e.user_id = :user)
				and e.occurred_at >= :from and e.occurred_at < :to
			order by e.user_id, e.occurred_at, e.id
			""").param("user", userId).param("from", settings.dayStartUtc(from))
			.param("to", settings.dayStartUtc(to.plusDays(2))).query(EventLine.class).list();

		Map<Long, Attendance.User> users = new LinkedHashMap<>();
		Map<Long, List<EventLine>> byUser = new LinkedHashMap<>();
		for (EventLine row : rows) {
			users.putIfAbsent(row.userId(), new Attendance.User(row.userId(), row.userName()));
			byUser.computeIfAbsent(row.userId(), id -> new ArrayList<>()).add(row);
		}
		if (userId != null && !users.containsKey(userId)) {
			jdbc.sql("select id, name from users where id = :id").param("id", userId).query(Attendance.User.class)
				.optional().ifPresent(u -> users.put(u.id(), u));
		}
		Map<Long, Map<Integer, WorkSchedules.Day>> plans = schedules.effective(users.keySet());

		List<UserReport> result = new ArrayList<>();
		for (Attendance.User user : users.values()) {
			List<Day> days = days(byUser.getOrDefault(user.id(), List.of()), plans.get(user.id()), from, to);
			result.add(new UserReport(user, totals(days), days));
		}
		result.sort((a, b) -> a.user().name().compareToIgnoreCase(b.user().name()));
		return new Report(from, to, settings.roundingMinutes(), result);
	}

	private List<Day> days(List<EventLine> rows, Map<Integer, WorkSchedules.Day> plan, LocalDate from, LocalDate to) {
		LocalDateTime now = Timestamps.now();
		LocalDate today = settings.local(now).toLocalDate();
		Map<LocalDate, List<Interval>> byDay = new TreeMap<>();
		LocalDateTime openIn = null;
		for (EventLine row : rows) {
			if ("in".equals(row.kind())) {
				if (openIn != null) {
					add(byDay, new Interval(openIn, null, false));
				}
				openIn = row.occurredAt();
			} else if (openIn != null) {
				if (Duration.between(openIn, row.occurredAt()).compareTo(settings.forgottenAfter()) > 0) {
					add(byDay, new Interval(openIn, null, false));
				} else {
					add(byDay, new Interval(openIn, row.occurredAt(), false));
				}
				openIn = null;
			}
		}
		if (openIn != null) {
			boolean ongoing = !openIn.isBefore(now.minus(settings.forgottenAfter()));
			add(byDay, new Interval(openIn, ongoing ? now : null, ongoing));
		}

		List<Day> days = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			WorkSchedules.Day planned = plan == null ? null : schedules.dayOf(plan, date);
			days.add(day(date, byDay.getOrDefault(date, List.of()), planned, date.isBefore(today)));
		}
		return days;
	}

	private void add(Map<LocalDate, List<Interval>> byDay, Interval interval) {
		byDay.computeIfAbsent(settings.local(interval.in()).toLocalDate(), d -> new ArrayList<>()).add(interval);
	}

	private Day day(LocalDate date, List<Interval> intervals, WorkSchedules.Day plan, boolean past) {
		long planned = plan == null ? 0 : plan.plannedMinutes();
		if (intervals.isEmpty()) {
			boolean absent = past && planned > 0;
			return new Day(date, null, null, 0, 0, 0, planned, 0, absent ? planned : 0, 0, false, absent, false);
		}
		long present = 0;
		long gaps = 0;
		LocalDateTime previousOut = null;
		boolean missingCheckout = false;
		boolean inside = false;
		LocalDateTime lastOut = null;
		for (Interval interval : intervals) {
			if (interval.out() == null) {
				missingCheckout = true;
				previousOut = null;
				continue;
			}
			if (previousOut != null && interval.in().isAfter(previousOut)) {
				gaps += Duration.between(previousOut, interval.in()).toMinutes();
			}
			present += Duration.between(interval.in(), interval.out()).toMinutes();
			previousOut = interval.out();
			if (interval.open()) {
				inside = true;
			} else {
				lastOut = interval.out();
			}
		}
		long assignedBreak = plan == null ? 0 : plan.breakMinutes();
		long deducted = Math.min(present, Math.max(0, assignedBreak - gaps));
		long worked = present - deducted;
		long overtime = round(Math.max(0, worked - planned));
		boolean finished = past && !inside;
		long missing = finished && planned > 0 ? Math.max(0, planned - worked) : 0;
		LocalDateTime firstIn = intervals.getFirst().in();
		long late = plan == null ? 0
			: Math.max(0, Duration.between(date.atTime(plan.startsAt()), settings.local(firstIn)).toMinutes());
		return new Day(date, firstIn, lastOut, present, gaps + deducted, worked, planned, overtime, missing, late,
			missingCheckout, false, inside);
	}

	private long round(long minutes) {
		int step = settings.roundingMinutes();
		return step <= 0 ? minutes : minutes / step * step;
	}

	private static Totals totals(List<Day> days) {
		long worked = 0;
		long planned = 0;
		long overtime = 0;
		long missing = 0;
		int workedDays = 0;
		int absent = 0;
		int missingCheckouts = 0;
		for (Day day : days) {
			worked += day.workedMinutes();
			planned += day.plannedMinutes();
			overtime += day.overtimeMinutes();
			missing += day.missingMinutes();
			workedDays += day.firstIn() == null ? 0 : 1;
			absent += day.absent() ? 1 : 0;
			missingCheckouts += day.missingCheckout() ? 1 : 0;
		}
		return new Totals(worked, planned, overtime, missing, workedDays, absent, missingCheckouts);
	}

}
