package com.olivamore.api.analytics.analytics;

import org.springframework.web.bind.annotation.BindParam;

/** Rapor olusturucu sorgusu; dogrulamayi aralik kontrolunden sonra servis yapar. */
record ReportQuery(
	String from,

	String to,

	String metric,

	String group,

	String chart,

	@BindParam("supplier_id")
	String supplierId,

	@BindParam("supplier_field_id")
	String supplierFieldId,

	@BindParam("product_type_id")
	String productTypeId,

	@BindParam("customer_id")
	String customerId,

	@BindParam("finished_product_id")
	String finishedProductId) {
}
