package com.olivamore.api.notification;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.notification.NotificationResponses.Marked;
import com.olivamore.api.notification.NotificationResponses.Notification;
import com.olivamore.api.notification.NotificationResponses.NudgeResult;
import com.olivamore.api.notification.NotificationResponses.NudgeTarget;
import com.olivamore.api.notification.NotificationResponses.UnreadCount;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.Pages;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bildirimler kullanicinin kendisine ait (ayri izin yok); durtme {@code notification.send} ister. */
@RestController
class NotificationController {

	private static final int DEFAULT_PAGE_SIZE = 30;
	private static final int MAX_PAGE_SIZE = 100;

	private final NotificationService service;

	NotificationController(NotificationService service) {
		this.service = service;
	}

	/** Her zaman sayfali: varsayilan 30, en fazla 100 kayit; {@code ?unread=true} yalnizca okunmamislar. */
	@GetMapping("/api/notifications")
	PagedModel<Notification> index(@RequestParam(required = false) Boolean unread,
		@PageableDefault(size = DEFAULT_PAGE_SIZE) Pageable pageable, @AuthenticationPrincipal CurrentUser user) {
		if (pageable.getPageSize() > MAX_PAGE_SIZE) {
			throw ValidationFailedException.of("size", MessageKeys.MAX, "value", MAX_PAGE_SIZE);
		}
		return Pages.of(pageable, requested -> {
			Page<UserNotification> rows = service.page(user.id(), Boolean.TRUE.equals(unread), requested);
			return new PageImpl<>(service.withSenders(rows.getContent()), rows.getPageable(), rows.getTotalElements());
		}, List::of);
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
