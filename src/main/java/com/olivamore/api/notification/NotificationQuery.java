package com.olivamore.api.notification;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.BindParam;

record NotificationQuery(
	Boolean unread,

	@BindParam("per_page")
	@Min(value = 1, message = LaravelMessages.MIN_1)
	@Max(value = 100, message = LaravelMessages.MAX_VALUE_100)
	Integer perPage) {

	private static final int DEFAULT_PER_PAGE = 30;

	boolean onlyUnread() {
		return Boolean.TRUE.equals(unread);
	}

	int size() {
		return perPage == null ? DEFAULT_PER_PAGE : perPage;
	}

}
