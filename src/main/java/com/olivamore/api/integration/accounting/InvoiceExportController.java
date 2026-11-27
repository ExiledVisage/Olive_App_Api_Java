package com.olivamore.api.integration.accounting;

import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Faturalarin muhasebe programina gonderimi: liste, elle gonderme ve yeniden deneme (docs/INVOICES.md). */
@RestController
class InvoiceExportController {

	/**
	 * @param status         pending, sent, formalizing, formalized, failed, cancelled
	 * @param eDocumentType  e_invoice, e_archive; resmilestirilmediyse bos
	 * @param officialNumber resmi fatura numarasi; henuz yoksa bos
	 */
	record ExportView(long id, long connectionId, String connectionName, long invoiceId, String invoiceNumber,
		String invoiceStatus, LocalDate issueDate, String buyerName, BigDecimal grossTotal, String status,
		String externalId, String eDocumentType, String officialNumber, String error, LocalDateTime attemptedAt,
		LocalDateTime sentAt, LocalDateTime formalizedAt) {
	}

	record Retried(int sent) {
	}

	private static final String SELECT = """
		select x.id, x.connection_id, c.name as connection_name, x.invoice_id, i.number as invoice_number,
			i.status as invoice_status, i.issue_date, i.buyer_name, i.gross_total, x.status, x.external_id,
			x.e_document_type, x.official_number, x.error, x.attempted_at, x.sent_at, x.formalized_at
		from integration_invoices x
		join invoices i on i.id = x.invoice_id
		join integration_connections c on c.id = x.connection_id
		""";

	private final InvoiceExport exports;
	private final JdbcClient jdbc;

	InvoiceExportController(InvoiceExport exports, JdbcClient jdbc) {
		this.exports = exports;
		this.jdbc = jdbc;
	}

	/** Bu baglantiya gonderilen faturalar, en yeni once (son 200). */
	@GetMapping("/api/integrations/connections/{id}/invoices")
	@PreAuthorize("hasAuthority('integration.view')")
	DataResponse<List<ExportView>> index(@PathVariable long id) {
		return DataResponse.of(jdbc.sql(SELECT + " where x.connection_id = :id order by x.id desc limit 200")
			.param("id", id).query(ExportView.class).list());
	}

	/** Bir faturanin butun muhasebe baglantilarindaki durumu (fatura ekrani). */
	@GetMapping("/api/integrations/invoices/{invoiceId}")
	@PreAuthorize("hasAuthority('integration.view')")
	DataResponse<List<ExportView>> forInvoice(@PathVariable long invoiceId) {
		return DataResponse.of(jdbc.sql(SELECT + " where x.invoice_id = :invoice order by x.id")
			.param("invoice", invoiceId).query(ExportView.class).list());
	}

	/** Faturayi gonderir ya da kaldigi adimdan devam eder. */
	@PostMapping("/api/integrations/connections/{id}/invoices/{invoiceId}/send")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<ExportView> send(@PathVariable long id, @PathVariable long invoiceId) {
		InvoiceExport.Export export = exports.send(id, invoiceId);
		return DataResponse.of(jdbc.sql(SELECT + " where x.id = :id").param("id", export.id())
			.query(ExportView.class).optional().orElseThrow(() -> new ModelNotFoundException("Invoice", invoiceId)));
	}

	/** Gonderilemeyen ve resmilestirmesi bitmeyen faturalari hemen yeniden dener. */
	@PostMapping("/api/integrations/connections/{id}/invoices/retry")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<Retried> retry(@PathVariable long id) {
		return DataResponse.of(new Retried(exports.retry(id, true)));
	}

}
