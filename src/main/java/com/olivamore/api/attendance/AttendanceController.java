package com.olivamore.api.attendance;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Giris-cikis ve mesai (docs/ATTENDANCE.md). */
@RestController
class AttendanceController {

	private final Attendance attendance;
	private final AttendanceReport reports;
	private final WorkSchedules schedules;

	AttendanceController(Attendance attendance, AttendanceReport reports, WorkSchedules schedules) {
		this.attendance = attendance;
		this.reports = reports;
		this.schedules = schedules;
	}

	/** Giris kartim: kisinin kisa omurlu QR kodu ve iceride mi. */
	@GetMapping("/api/attendance/my-code")
	DataResponse<Attendance.MyCode> myCode(@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(attendance.myCode(user.id()));
	}

	/** Kendi mesaim: {@code from}-{@code to} yerel gunler. */
	@GetMapping("/api/attendance/me")
	DataResponse<AttendanceReport.UserReport> me(@AuthenticationPrincipal CurrentUser user,
		@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return DataResponse.of(reports.report(from, to, user.id()).users().getFirst());
	}

	/** Kapi okuyucu (bekci, kapi tableti). */
	@PostMapping("/api/attendance/scan")
	@PreAuthorize("hasAuthority('attendance.scan')")
	DataResponse<Attendance.ScanResult> scan(@RequestBody Attendance.ScanRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(attendance.scan(request, user.id()));
	}

	@GetMapping("/api/attendance/inside")
	@PreAuthorize("hasAnyAuthority('attendance.view', 'attendance.scan')")
	ListResponse<Attendance.Inside> inside() {
		return new ListResponse<>(attendance.inside());
	}

	/** Kisi secicisi (elle kayit, kisiye ozel plan). */
	@GetMapping("/api/attendance/people")
	@PreAuthorize("hasAuthority('attendance.view')")
	ListResponse<Attendance.User> people() {
		return new ListResponse<>(attendance.people());
	}

	/** Kisi x gun mesai raporu; {@code user_id} verilmezse araliktaki herkes. */
	@GetMapping("/api/attendance/report")
	@PreAuthorize("hasAuthority('attendance.view')")
	DataResponse<AttendanceReport.Report> report(
		@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@RequestParam(name = "user_id", required = false) Long userId) {
		return DataResponse.of(reports.report(from, to, userId));
	}

	/** Ham kayitlar (iptaller dahil). */
	@GetMapping("/api/attendance/events")
	@PreAuthorize("hasAuthority('attendance.view')")
	ListResponse<Attendance.Event> events(
		@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@RequestParam(name = "user_id", required = false) Long userId) {
		return new ListResponse<>(attendance.events(userId, from, to));
	}

	/** Elle kayit (unutulan cikis, okutulamayan giris); gerekce zorunlu. */
	@PostMapping("/api/attendance/events")
	@PreAuthorize("hasAuthority('attendance.manage')")
	ResponseEntity<DataResponse<Attendance.Event>> record(@RequestBody Attendance.ManualRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(attendance.record(request, user.id()));
	}

	@PostMapping("/api/attendance/events/{id}/void")
	@PreAuthorize("hasAuthority('attendance.manage')")
	DataResponse<Attendance.Event> voidEvent(@PathVariable long id,
		@RequestBody(required = false) Attendance.VoidRequest request, @AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(attendance.voidEvent(id, request, user.id()));
	}

	/** Fabrika geneli plan ve {@code user_id} verilirse kisinin plani. */
	@GetMapping("/api/attendance/schedules")
	@PreAuthorize("hasAuthority('attendance.view')")
	DataResponse<WorkSchedules.Plan> schedules(@RequestParam(name = "user_id", required = false) Long userId) {
		return DataResponse.of(schedules.plan(userId));
	}

	@PutMapping("/api/attendance/schedules")
	@PreAuthorize("hasAuthority('attendance.manage')")
	DataResponse<WorkSchedules.Plan> replaceSchedule(@RequestBody WorkSchedules.PlanRequest request) {
		return DataResponse.of(schedules.replace(request));
	}

	/** Kisiye ozel plani kaldirir. */
	@DeleteMapping("/api/attendance/schedules/users/{userId}")
	@PreAuthorize("hasAuthority('attendance.manage')")
	DataResponse<WorkSchedules.Plan> clearSchedule(@PathVariable long userId) {
		return DataResponse.of(schedules.clearPersonal(userId));
	}

}
