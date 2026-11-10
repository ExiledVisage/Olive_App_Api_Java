package com.olivamore.api.notification;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.notification.NotificationResponses.Marked;
import com.olivamore.api.notification.NotificationResponses.Notification;
import com.olivamore.api.notification.NotificationResponses.NudgeResult;
import com.olivamore.api.notification.NotificationResponses.NudgeTarget;
import com.olivamore.api.notification.NotificationResponses.UnreadCount;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bildirimler kullanicinin kendisine ait (ayri izin yok); durtme {@code notification.send} ister. */
@RestController
class NotificationController {

	private final NotificationService service;

	NotificationController(NotificationService service) {
		this.service = service;
	}

	@GetMapping("/api/notifications")
	ListResponse<Notification> index(@Valid @ModelAttribute NotificationQuery query, HttpServletRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		int page = Listing.currentPage(request);
		Page<UserNotification> rows = service.page(user.id(), query.onlyUnread(), page, query.size());
		return LaravelPaginator.paginateWithoutQueryString(service.withSenders(rows.getContent()),
			rows.getTotalElements(), page, query.size(), request);
	}

	@GetMapping("/api/notifications/unread-count")
	DataResponse<UnreadCount> unreadCount(@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(new UnreadCount(service.unreadCount(user.id())));
	}

	@PostMapping("/api/notifications/{id}/read")
	DataResponse<Notification> markRead(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(service.markRead(id, user.id()));
	}

	@PostMapping("/api/notifications/read-all")
	DataResponse<Marked> markAllRead(@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.of(new Marked(service.markAllRead(user.id())));
	}

	@PostMapping("/api/nudges")
	@PreAuthorize("hasAuthority('notification.send')")
	ResponseEntity<DataResponse<NudgeResult>> nudge(@Valid @RequestBody NudgeRequest request,
		@AuthenticationPrincipal CurrentUser actor) {
		return DataResponse.created(service.nudge(actor, request));
	}

	@GetMapping("/api/nudges/targets")
	@PreAuthorize("hasAuthority('notification.send')")
	DataResponse<List<NudgeTarget>> nudgeTargets(@AuthenticationPrincipal CurrentUser actor) {
		return DataResponse.of(service.targets(actor));
	}

}
