package com.olivamore.api.shared.web;

import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.validation.MessageKeys;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Sayfali listeler: {@code ?page=0&size=20} (0 tabanli, en fazla 200; ikisi birlikte verilir, bkz.
 * {@link PageParameters}). Verilmezse butun kayitlar tek sayfada doner. Cevap Spring Data'nin {@link PagedModel}'i:
 * <pre>{"content": [...], "page": {"size": 20, "number": 0, "total_elements": 45, "total_pages": 3}}</pre>
 * Siralama endpoint'e sabittir; istemcinin sectigi siralamaya izin verilen alan yoktur, {@code sort} 422 alir.
 */
public final class Pages {

	private Pages() {
	}

	/**
	 * @param paged istenen sayfa (siralama endpoint'in kendi sirasidir)
	 * @param all sayfa istenmediginde butun kayitlar
	 */
	public static <T> PagedModel<T> of(Pageable pageable, Function<Pageable, Page<T>> paged,
		Supplier<List<T>> all) {
		if (pageable.getSort().isSorted()) {
			throw ValidationFailedException.of("sort", MessageKeys.IN);
		}
		return new PagedModel<>(pageable.isPaged() ? paged.apply(pageable) : new PageImpl<>(all.get()));
	}

	/** Istenen sayfa, endpoint'in sirasiyla. */
	public static PageRequest sorted(Pageable pageable, Sort order) {
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order);
	}

}
