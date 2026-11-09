package com.olivamore.api.notification;

import com.olivamore.api.shared.persistence.AuditedEntity;
import com.olivamore.api.shared.time.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Kullaniciya dusen uygulama ici bildirim. */
@Entity
@Table(name = "user_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserNotification extends AuditedEntity {

	private static final String TYPE_NUDGE = "nudge";
	private static final String CHANNEL_IN_APP = "in_app";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	private Long sentByUserId;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String channel;

	@Column(nullable = false)
	private String title;

	private String body;

	private String subjectType;

	private Long subjectId;

	private LocalDateTime readAt;

	static UserNotification nudge(long userId, long senderId, NudgeSubject subject, long subjectId, String body) {
		UserNotification notification = new UserNotification();
		notification.userId = userId;
		notification.sentByUserId = senderId;
		notification.type = TYPE_NUDGE;
		notification.channel = CHANNEL_IN_APP;
		notification.title = subject.title();
		notification.body = body;
		notification.subjectType = subject.value();
		notification.subjectId = subjectId;
		return notification;
	}

	void markRead() {
		if (readAt == null) {
			readAt = Timestamps.now();
		}
	}

}
