package com.olivamore.api.notification;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code NotificationController}. Bildirimler kullanicinin kendisine ait (ayri izin yok); durtme
 * {@code notification.send} ister.
 */
@RestController
class NotificationController {

	private final LaravelValidator validator;
	private final NotificationService notifications;
	private final UserNotificationResource resource;

	NotificationController(LaravelValidator validator, NotificationService notifications, UserNotificationResource resource) {
		this.validator = validator;
		this.notifications = notifications;
		this.resource = resource;
	}

	/** Her zaman sayfali ({@code per_page} varsayilan 30, en fazla 100); {@code ?unread=1} yalnizca okunmamislar; en yeni once. */
	@GetMapping("/api/notifications")
	ListResponse<Map<String, Object>> index(HttpServletRequest request, RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, NotificationRequests.index());
		boolean unread = v.get("unread") != null && Boolean.TRUE.equals(Casts.bool(v.get("unread")));
		int perPage = v.get("per_page") == null ? 30 : (int) Php.intval(v.get("per_page"));
		int page = Listing.currentPage(request);
		long total = notifications.count(user.id(), unread);
		List<Row> rows = total == 0 ? List.of() : notifications.page(user.id(), unread, page, perPage);
		// Laravel paginate() withQueryString'siz: linklerde unread/per_page yok
		return LaravelPaginator.paginateWithoutQueryString(resource.of(rows, true), total, page, perPage, request);
	}

	/** Zil ikonu: okunmamis sayisi. */
	@GetMapping("/api/notifications/unread-count")
	Map<String, Object> unreadCount(CurrentUser user) {
		return Map.of("data", Map.of("unread", notifications.unreadCount(user.id())));
	}

	@PostMapping("/api/notifications/{notification}/read")
	@Transactional
	Map<String, Object> markRead(@Bound(value = "notification", table = "user_notifications", model = "UserNotification") Row notification,
		CurrentUser user) {
		return Map.of("data", resource.of(List.of(notifications.markRead(notification, user.id())), true).getFirst());
	}

	@PostMapping("/api/notifications/read-all")
	@Transactional
	Map<String, Object> markAllRead(CurrentUser user) {
		return Map.of("data", Map.of("marked", notifications.markAllRead(user.id())));
	}

	/** Isciyi durt (201). */
	@PostMapping("/api/nudges")
	@RequiresPermission("notification.send")
	@Transactional
	ResponseEntity<Map<String, Object>> nudge(RequestInput input, CurrentUser actor) {
		ValidatedInput v = validator.validate(input, NotificationRequests.nudge());
		List<Row> created = notifications.nudge(actor, Php.strval(v.get("subject_type")), Php.intval(v.get("subject_id")),
			v.get("user_id"), v.get("note"));
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("sent_to", created.size());
		data.put("notifications", resource.of(created, false));
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
	}

	/** "Belirli bir kisiyi uyar" listesi. */
	@GetMapping("/api/nudges/targets")
	@RequiresPermission("notification.send")
	Map<String, Object> nudgeTargets(CurrentUser actor) {
		return Map.of("data", notifications.nudgeTargets(actor));
	}

}
