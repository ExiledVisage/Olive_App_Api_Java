package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsResponse.CustomerDetail;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Customers;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Report;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Suppliers;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Yield;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Analiz ekrani ve rapor olusturucu; hepsi {@code ?from=Y-m-d&to=Y-m-d} ister, en fazla iki yil. */
@RestController
@PreAuthorize("hasAuthority('analytics.view')")
class AnalyticsController {

	private final AnalyticsService analytics;
	private final SupplierComparison comparison;

	AnalyticsController(AnalyticsService analytics, SupplierComparison comparison) {
		this.analytics = analytics;
		this.comparison = comparison;
	}

	/** {@code ?product_type_id=}, {@code ?supplier_id=} ile daraltilabilir. */
	@GetMapping("/api/analytics/yield")
	DataResponse<Yield> yield(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
		@RequestParam(name = "product_type_id", required = false) String productTypeId,
		@RequestParam(name = "supplier_id", required = false) String supplierId) {
		return DataResponse.of(analytics.yield(from, to, productTypeId, supplierId));
	}

	@GetMapping("/api/analytics/suppliers")
	DataResponse<Suppliers> suppliers(@RequestParam(required = false) String from,
		@RequestParam(required = false) String to,
		@RequestParam(name = "product_type_id", required = false) String productTypeId) {
		return DataResponse.of(analytics.suppliers(from, to, productTypeId));
	}

	/**
	 * Tedarikci karsilastirma: {@code ?ids=1,2,3} (2-50), {@code ?metrics=} (verilmezse hepsi), donem ve istege
	 * bagli urun tipi; {@code ?series=} tek ozelligin aylik degerleri. Maliyet ve kanal yalnizca fiyat yetkisiyle.
	 */
	@GetMapping("/api/analytics/suppliers/compare")
	DataResponse<SupplierComparison.Comparison> compareSuppliers(@RequestParam(required = false) String ids,
		@RequestParam(required = false) String metrics, @RequestParam(required = false) String from,
		@RequestParam(required = false) String to,
		@RequestParam(name = "product_type_id", required = false) String productTypeId,
		@RequestParam(required = false) String series, Authentication authentication) {
		return DataResponse.of(comparison.compare(ids, metrics, from, to, productTypeId, series, prices(authentication)));
	}

	/** Karsilastirilabilen ozellikler (kullanicinin yetkisine gore). */
	@GetMapping("/api/analytics/suppliers/compare/metrics")
	DataResponse<List<SupplierComparison.Metric>> compareMetrics(Authentication authentication) {
		return DataResponse.of(SupplierComparison.visibleMetrics(prices(authentication)));
	}

	private static boolean prices(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
			.anyMatch(a -> SupplierComparison.PRICE_PERMISSION.equals(a.getAuthority()));
	}

	@GetMapping("/api/analytics/customers")
	DataResponse<Customers> customers(@RequestParam(required = false) String from,
		@RequestParam(required = false) String to) {
		return DataResponse.of(analytics.customers(from, to));
	}

	@GetMapping("/api/analytics/customers/{id}")
	DataResponse<CustomerDetail> customer(@PathVariable long id, @RequestParam(required = false) String from,
		@RequestParam(required = false) String to) {
		return DataResponse.of(analytics.customer(id, from, to));
	}

	@GetMapping("/api/analytics/catalog")
	DataResponse<ReportSpec.Catalog> catalog() {
		return DataResponse.of(ReportSpec.catalog());
	}

	/** {@code ?metric=&group=&chart=} ve kaynaga uyan filtreler; tutarsiz secim 422. */
	@GetMapping("/api/analytics/query")
	DataResponse<Report> query(@ModelAttribute ReportQuery query) {
		return DataResponse.of(analytics.report(query));
	}

}
