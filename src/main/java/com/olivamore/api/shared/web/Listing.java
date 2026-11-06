package com.olivamore.api.shared.web;

import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Laravel'deki {@code ResolvesPerPage} + {@code PaginatesQueries} ikilisinin karsiligi.
 * <p>
 * Sayfalama opt-in'dir: {@code per_page} yoksa butun kayitlar sayfasiz doner.
 * <pre>
 * return Listing.of(request,
 *     pageable -> repository.findAll(spec, pageable),
 *     () -> repository.findAll(spec),
 *     mapper::toResource);
 * </pre>
 */
public final class Listing {

	static final String PER_PAGE = "per_page";
	static final int MAX_PER_PAGE = 200;

	private Listing() {
	}

	public static <E, R> ListResponse<R> of(HttpServletRequest request, Function<Pageable, Page<E>> paged,
		Supplier<List<E>> all, Function<E, R> mapper) {
		Optional<Integer> perPage = perPage(request);
		if (perPage.isEmpty()) {
			return new ListResponse.Plain<>(all.get().stream().map(mapper).toList());
		}
		int page = currentPage(request);
		Page<E> result = paged.apply(PageRequest.of(page - 1, perPage.get()));
		return LaravelPaginator.paginate(result.getContent().stream().map(mapper).toList(), result.getTotalElements(),
			page, perPage.get(), request);
	}

	/**
	 * {@code ResolvesPerPage::perPage()}: kurallar {@code sometimes|integer|min:1|max:200}.
	 * Laravel her kurali ayri degerlendirir; bos deger (null) hem integer hem min'e takilir.
	 */
	public static Optional<Integer> perPage(HttpServletRequest request) {
		if (!InputNormalizationFilter.wasSent(request, PER_PAGE)) {
			return Optional.empty();
		}
		String raw = request.getParameter(PER_PAGE);
		List<String> errors = new ArrayList<>();
		OptionalLong value = PhpNumbers.validateInt(raw);
		if (value.isEmpty()) {
			errors.add(LaravelMessages.format(LaravelMessages.INTEGER, PER_PAGE));
		}
		double size = PhpNumbers.size(raw);
		if (size < 1) {
			errors.add(LaravelMessages.format(LaravelMessages.minNumeric(1), PER_PAGE));
		}
		if (size > MAX_PER_PAGE) {
			errors.add(LaravelMessages.format(LaravelMessages.maxNumeric(MAX_PER_PAGE), PER_PAGE));
		}
		if (!errors.isEmpty()) {
			Map<String, List<String>> map = new LinkedHashMap<>();
			map.put(PER_PAGE, errors);
			throw new LaravelValidationException(map);
		}
		return Optional.of((int) value.getAsLong());
	}

	/** {@code Paginator::resolveCurrentPage()}: gecerli tamsayi ve >= 1 degilse 1. */
	public static int currentPage(HttpServletRequest request) {
		OptionalLong page = PhpNumbers.validateInt(request.getParameter(LaravelPaginator.PAGE));
		if (page.isEmpty() || page.getAsLong() < 1 || page.getAsLong() > Integer.MAX_VALUE) {
			return 1;
		}
		return (int) page.getAsLong();
	}

}
