package com.olivamore.api.shared.web;

import java.util.List;

/** Sayfasiz liste: {@code {"data": [...]}}. Sayfali listeler {@link Pages}. */
public record ListResponse<T>(List<T> data) {
}
