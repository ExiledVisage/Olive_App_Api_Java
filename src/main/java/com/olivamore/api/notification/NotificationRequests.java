package com.olivamore.api.notification;

import com.olivamore.api.shared.validation.Rules;

/** Laravel {@code Http/Requests/Notification} ve controller'daki {@code $request->validate}. */
final class NotificationRequests {

	private NotificationRequests() {
	}

	/** Bildirim listesi: {@code ?unread}, {@code ?per_page} (en fazla 100). */
	static Rules index() {
		return Rules.of()
			.field("unread", "nullable|boolean")
			.field("per_page", "nullable|integer|min:1|max:100");
	}

	/** SendNudgeRequest */
	static Rules nudge() {
		return Rules.of()
			.field("subject_type", "required|string|in:" + String.join(",", NotificationService.subjectTypes()))
			.field("subject_id", "required|integer|min:1")
			.field("user_id", "nullable|integer|exists:users,id")
			.field("note", "nullable|string|max:500");
	}

}
