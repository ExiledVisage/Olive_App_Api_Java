package com.olivamore.api.notification;

import java.time.LocalDateTime;
import java.util.List;

/** Bildirim endpoint'lerinin cevaplari. */
final class NotificationResponses {

	record Sender(Long id, String name) {
	}

	record Notification(
		Long id,
		String type,
		String title,
		String body,
		String subjectType,
		Long subjectId,
		boolean isRead,
		LocalDateTime readAt,
		LocalDateTime createdAt,
		Sender sentBy) {

		static Notification of(UserNotification n, Sender sender) {
			return new Notification(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getSubjectType(),
				n.getSubjectId(), n.getReadAt() != null, n.getReadAt(), n.getCreatedAt(), sender);
		}

	}

	record UnreadCount(long unread) {
	}

	record Marked(int marked) {
	}

	record NudgeResult(int sentTo, List<Notification> notifications) {
	}

	record NudgeTarget(Long id, String name, String email, List<String> roles) {
	}

	private NotificationResponses() {
	}

}
