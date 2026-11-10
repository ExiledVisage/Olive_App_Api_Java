package com.olivamore.api.analytics.analytics;

import com.olivamore.api.analytics.analytics.AnalyticsResponse.CustomerDetail;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Customers;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Report;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Suppliers;
import com.olivamore.api.analytics.analytics.AnalyticsResponse.Yield;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Analiz ekrani ve rapor olusturucu; hepsi {@code ?from=Y-m-d&to=Y-m-d} ister, en fazla iki yil. */
@RestController
@RequiresPermission("analytics.view")
class AnalyticsController {

	private final AnalyticsService analytics;

	AnalyticsController(AnalyticsService analytics) {
		this.analytics = analytics;
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
