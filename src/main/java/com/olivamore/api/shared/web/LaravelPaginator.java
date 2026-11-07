package com.olivamore.api.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code LengthAwarePaginator} + {@code UrlWindow} cikisinin birebir kopyasi
 * (Laravel 12, {@code onEachSide = 3}).
 * <p>
 * Link URL'leri: istegin yolu + mevcut query parametreleri (sirasi korunarak,
 * {@code page} haric) + en sonda {@code page}. {@code withQueryString()} davranisi.
 */
public final class LaravelPaginator {

	static final int ON_EACH_SIDE = 3;
	static final String PREVIOUS = "&laquo; Previous";
	static final String NEXT = "Next &raquo;";
	static final String PAGE = "page";

	private LaravelPaginator() {
	}

	/**
	 * @param items bu sayfanin elemanlari
	 * @param total toplam kayit sayisi
	 * @param currentPage istenen sayfa (son sayfadan buyuk olabilir; Laravel bos sayfa doner)
	 */
	public static <T> ListResponse.Paginated<T> paginate(List<T> items, long total, int currentPage, int perPage,
		HttpServletRequest request) {
		return paginate(items, total, currentPage, perPage, Urls.of(request));
	}

	/** {@code withQueryString()} olmadan {@code paginate()}: link URL'lerinde yalnizca {@code page}. */
	public static <T> ListResponse.Paginated<T> paginateWithoutQueryString(List<T> items, long total, int currentPage, int perPage,
		HttpServletRequest request) {
		return paginate(items, total, currentPage, perPage, new Urls(Urls.of(request).path(), Map.of()));
	}

	private static <T> ListResponse.Paginated<T> paginate(List<T> items, long total, int currentPage, int perPage, Urls urls) {
		int lastPage = (int) Math.max(Math.ceil((double) total / perPage), 1);
		boolean hasMore = currentPage < lastPage;

		Integer from = items.isEmpty() ? null : (currentPage - 1) * perPage + 1;
		Integer to = items.isEmpty() ? null : from + items.size() - 1;
		String prev = currentPage > 1 ? urls.page(currentPage - 1) : null;
		String next = hasMore ? urls.page(currentPage + 1) : null;

		List<Map<String, Object>> links = new ArrayList<>();
		links.add(link(prev, PREVIOUS, currentPage > 1 ? currentPage - 1 : null, false));
		for (Object element : elements(currentPage, lastPage)) {
			if (element instanceof Integer page) {
				links.add(link(urls.page(page), String.valueOf(page), page, page == currentPage));
			}
			else {
				Map<String, Object> dots = new LinkedHashMap<>();
				dots.put("url", null);
				dots.put("label", "...");
				dots.put("active", false);
				links.add(dots);
			}
		}
		links.add(link(next, NEXT, hasMore ? currentPage + 1 : null, false));

		return new ListResponse.Paginated<>(items,
			new ListResponse.Links(urls.page(1), urls.page(lastPage), prev, next),
			new ListResponse.Meta(currentPage, from, lastPage, links, urls.path(), perPage, to, total));
	}

	/**
	 * {@code UrlWindow::get()} + {@code elements()}: sayfa numaralari ve "..." yer tutuculari.
	 * Donen liste Integer (sayfa) ya da String ("...") icerir.
	 */
	static List<Object> elements(int currentPage, int lastPage) {
		List<Object> out = new ArrayList<>();
		if (lastPage < ON_EACH_SIDE * 2 + 8) {
			range(out, 1, lastPage);
			return out;
		}
		int window = ON_EACH_SIDE + 4;
		if (currentPage <= window) {
			range(out, 1, window + ON_EACH_SIDE);
			out.add("...");
			range(out, lastPage - 1, lastPage);
		}
		else if (currentPage > lastPage - window) {
			range(out, 1, 2);
			out.add("...");
			range(out, lastPage - (window + (ON_EACH_SIDE - 1)), lastPage);
		}
		else {
			range(out, 1, 2);
			out.add("...");
			range(out, currentPage - ON_EACH_SIDE, currentPage + ON_EACH_SIDE);
			out.add("...");
			range(out, lastPage - 1, lastPage);
		}
		return out;
	}

	private static void range(List<Object> out, int from, int to) {
		for (int i = from; i <= to; i++) {
			out.add(i);
		}
	}

	private static Map<String, Object> link(String url, String label, Integer page, boolean active) {
		Map<String, Object> link = new LinkedHashMap<>();
		link.put("url", url);
		link.put("label", label);
		link.put("page", page);
		link.put("active", active);
		return link;
	}

	/** {@code AbstractPaginator::url()} */
	record Urls(String path, Map<String, String> query) {

		static Urls of(HttpServletRequest request) {
			String path = ServletUriComponentsBuilder.fromRequest(request).replaceQuery(null).build().toUriString();
			Map<String, String> query = new LinkedHashMap<>();
			request.getParameterMap().forEach((name, values) -> {
				if (!PAGE.equals(name) && values.length > 0 && values[0] != null) {
					query.put(name, values[0]);
				}
			});
			return new Urls(path, query);
		}

		String page(int page) {
			Map<String, String> parameters = new LinkedHashMap<>(query);
			parameters.put(PAGE, String.valueOf(Math.max(page, 1)));
			StringBuilder url = new StringBuilder(path).append(path.contains("?") ? '&' : '?');
			boolean first = true;
			for (Map.Entry<String, String> e : parameters.entrySet()) {
				if (!first) {
					url.append('&');
				}
				url.append(rfc3986(e.getKey())).append('=').append(rfc3986(e.getValue()));
				first = false;
			}
			return url.toString();
		}

		/** PHP {@code rawurlencode} (Arr::query, PHP_QUERY_RFC3986): yalnizca A-Z a-z 0-9 - _ . ~ kalir. */
		static String rfc3986(String value) {
			StringBuilder out = new StringBuilder();
			for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
				int c = b & 0xFF;
				if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
					|| c == '-' || c == '_' || c == '.' || c == '~') {
					out.append((char) c);
				}
				else {
					out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
						.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
				}
			}
			return out.toString();
		}

	}

}
