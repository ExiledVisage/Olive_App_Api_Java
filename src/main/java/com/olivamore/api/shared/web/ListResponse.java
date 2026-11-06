package com.olivamore.api.shared.web;

import java.util.List;
import java.util.Map;

/**
 * Laravel'in liste cevaplari. {@code per_page} yoksa {@link Plain}, varsa {@link Paginated}.
 */
public sealed interface ListResponse<T> permits ListResponse.Plain, ListResponse.Paginated {

	List<T> data();

	/** Sayfasiz: {@code {"data": [...]}} */
	record Plain<T>(List<T> data) implements ListResponse<T> {
	}

	/**
	 * Laravel {@code ResourceCollection} + {@code LengthAwarePaginator}:
	 * <pre>{"data": [...], "links": {first, last, prev, next},
	 *  "meta": {current_page, from, last_page, links, path, per_page, to, total}}</pre>
	 */
	record Paginated<T>(List<T> data, Links links, Meta meta) implements ListResponse<T> {
	}

	record Links(String first, String last, String prev, String next) {
	}

	/**
	 * {@code links} elemanlari Map: "..." elemaninda {@code page} anahtari hic yok,
	 * onceki/sonraki elemanlarinda ise {@code null} olarak var.
	 */
	record Meta(int currentPage, Integer from, int lastPage, List<Map<String, Object>> links, String path,
		int perPage, Integer to, long total) {
	}

}
