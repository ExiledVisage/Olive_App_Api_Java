package com.olivamore.api.integration.accounting;

import com.olivamore.api.integration.connection.Connections;
import com.olivamore.api.integration.provider.AccountingClient;
import com.olivamore.api.sales.SalesInvoiceCancelled;
import com.olivamore.api.sales.SalesInvoiceDocuments;
import com.olivamore.api.sales.SalesInvoiceDocuments.Document;
import com.olivamore.api.sales.SalesInvoiceIssued;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Satis faturasinin muhasebe programina (Parasut) gonderimi (docs/INVOICES.md). Fatura kesilince, faturalari gonderen
 * her etkin muhasebe baglantisina islem bittikten sonra gider: cari ve urunler bulunur ya da acilir (karsiliklari
 * saklanir), satis faturasi KDV haric birim fiyatla olusur. Baglantida resmilestirme aciksa alici e-fatura mukellefi
 * ise e-fatura, degilse e-arsiv (site ve pazar yeri siparisinde internet satisi bilgisiyle) isi baslar ve sonucu
 * beklenir. Her adimin hatasi kayda yazilir; yeniden deneme kaldigi adimdan devam eder, faturayi geri almaz.
 */
@Service
public class InvoiceExport {

	private static final Logger log = LoggerFactory.getLogger(InvoiceExport.class);

	/** Basarisiz gonderimin zamanlayiciyla yeniden denenmesi icin bekleme. */
	static final int RETRY_MINUTES = 30;

	/** Resmilestirme isinin sonucu icin bekleme; bitmezse zamanlayici sonra bakar. */
	private static final int JOB_ATTEMPTS = 5;

	record Export(long id, long connectionId, long invoiceId, String status, String externalId, String eDocumentType,
		String jobId) {
	}

	private record Sale(String provider, String baseUrl) {
	}

	private record Formalized(long id, long connectionId, String officialNumber, String eDocumentType,
		String eDocumentId) {
	}

	/**
	 * Pazar yerine bildirilecek fatura.
	 *
	 * @param pdf resmi belgenin dosyasi; yoksa bos
	 */
	public record Ready(String number, LocalDate issueDate, byte[] pdf) {
	}

	/** Fatura resmilesti (muhasebe programinda); pazar yeri bildirimi dinler. */
	public record InvoiceFormalized(long invoiceId) {
	}

	private final JdbcClient jdbc;
	private final Connections connections;
	private final SalesInvoiceDocuments documents;
	private final Duration jobWait;
	private final ApplicationEventPublisher events;

	InvoiceExport(JdbcClient jdbc, Connections connections, SalesInvoiceDocuments documents,
		@Value("${olive.integration.accounting.job-wait:PT2S}") Duration jobWait, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.connections = connections;
		this.documents = documents;
		this.jobWait = jobWait;
		this.events = events;
	}

	/** Fatura kesildi: kaydedildikten sonra gonderilir. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onIssued(SalesInvoiceIssued event) {
		List<Long> targets = jdbc.sql("""
			select id from integration_connections
			where provider = 'parasut' and status = 'active' and invoice_push_enabled order by id
			""").query(Long.class).list();
		for (long connectionId : targets) {
			try {
				send(connectionId, event.invoiceId());
			} catch (RuntimeException e) {
				log.warn("Fatura {} baglanti {}'e gonderilemedi: {}", event.invoiceId(), connectionId, e.getMessage());
			}
		}
	}

	/**
	 * Fatura bizde iptal edildi: Parasut'teki fatura da iptal edilir. Resmilesmis belge buradan iptal edilmez; kayda
	 * Parasut'ten iptal edilmesi gerektigi yazilir.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onCancelled(SalesInvoiceCancelled event) {
		List<Export> exports = jdbc.sql(SELECT + " where invoice_id = :invoice and status <> 'cancelled'")
			.param("invoice", event.invoiceId()).query(Export.class).list();
		for (Export export : exports) {
			LocalDateTime now = Timestamps.now();
			if ("formalized".equals(export.status()) || export.jobId() != null) {
				error(export.id(), "Fatura bizde iptal edildi; resmileşmiş belgeyi Paraşüt'ten iptal edin.", now);
				continue;
			}
			try {
				if (export.externalId() != null) {
					connections.accounting(connections.connection(export.connectionId()))
						.cancelSalesInvoice(export.externalId());
				}
				jdbc.sql("""
					update integration_invoices set status = 'cancelled', error = null, attempted_at = :now,
						updated_at = :now where id = :id
					""").param("now", now).param("id", export.id()).update();
			} catch (RuntimeException e) {
				error(export.id(), "Paraşüt'te iptal edilemedi: " + message(e), now);
			}
		}
	}

	/** Faturayi bu baglantiya gonderir ya da kaldigi adimdan devam eder; kaydin son hali. */
	public Export send(long connectionId, long invoiceId) {
		Connections.Connection connection = connections.connection(connectionId);
		AccountingClient client = connections.accounting(connection);
		Document document = documents.document(invoiceId)
			.orElseThrow(() -> new ModelNotFoundException("Invoice", invoiceId));
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			insert into integration_invoices (connection_id, invoice_id, status, created_at, updated_at)
			values (:connection, :invoice, 'pending', :now, :now)
			on conflict (connection_id, invoice_id) do nothing
			""").param("connection", connectionId).param("invoice", invoiceId).param("now", now).update();
		Export export = export(connectionId, invoiceId);
		if (!"cancelled".equals(export.status()) && !"formalized".equals(export.status())
			&& "issued".equals(document.status())) {
			process(connection, client, document, export);
		}
		return export(connectionId, invoiceId);
	}

	/**
	 * Gonderilmemis ya da resmilestirmesi bitmemis faturalar: hic denenmemis ya da son denemesi
	 * {@value #RETRY_MINUTES} dakikadan eski olanlar (zamanlayici); {@code now} ise bekleme yok.
	 *
	 * @return gonderimi tamamlanan fatura sayisi
	 */
	public int retry(long connectionId, boolean now) {
		List<Long> invoices = jdbc.sql("""
			select x.invoice_id from integration_invoices x join invoices i on i.id = x.invoice_id
			where x.connection_id = :connection and x.status in ('pending', 'failed', 'formalizing')
				and i.status = 'issued' and (x.attempted_at is null or x.attempted_at < :before)
			order by x.id
			""").param("connection", connectionId)
			.param("before", Timestamps.now().plusMinutes(now ? 1 : -RETRY_MINUTES)).query(Long.class).list();
		int done = 0;
		for (long invoiceId : invoices) {
			String status = send(connectionId, invoiceId).status();
			if ("sent".equals(status) || "formalized".equals(status)) {
				done++;
			}
		}
		return done;
	}

	private void process(Connections.Connection connection, AccountingClient client, Document document,
		Export export) {
		LocalDateTime now = Timestamps.now();
		try {
			String externalId = export.externalId();
			if (externalId == null) {
				externalId = createSalesInvoice(connection.id(), client, document);
				jdbc.sql("""
					update integration_invoices set status = 'sent', external_id = :external, error = null,
						attempted_at = :now, sent_at = :now, updated_at = :now where id = :id
					""").param("external", externalId).param("now", now).param("id", export.id()).update();
			}
			if (connection.eDocumentEnabled()) {
				formalize(client, document, export, externalId);
			}
		} catch (RuntimeException e) {
			log.warn("Fatura {} gonderilemedi: {}", document.number(), e.getMessage());
			jdbc.sql("""
				update integration_invoices set status = 'failed', error = :error, attempted_at = :now,
					updated_at = :now where id = :id
				""").param("error", message(e)).param("now", now).param("id", export.id()).update();
		}
	}

	private String createSalesInvoice(long connectionId, AccountingClient client, Document document) {
		AccountingClient.Contact buyer = buyer(document);
		String contactId = reference(connectionId, "contact", document.customerId(), () -> client.contact(buyer));
		List<AccountingClient.Line> lines = new ArrayList<>();
		for (SalesInvoiceDocuments.Line line : document.lines()) {
			BigDecimal net = net(line.unitPrice(), line.vatRate());
			String productId = reference(connectionId, "product", line.finishedProductId(),
				() -> client.product(new AccountingClient.Product(line.description(), line.sku(), line.vatRate(),
					net)));
			lines.add(new AccountingClient.Line(productId, line.description(), line.quantity(),
				line.netAmount().divide(BigDecimal.valueOf(line.quantity()), 6, RoundingMode.HALF_UP), line.vatRate()));
		}
		return client.createSalesInvoice(new AccountingClient.SalesInvoice(contactId, document.number(),
			document.issueDate(), document.orderExternalRef() != null ? document.orderExternalRef()
				: "#" + document.orderId(), document.orderDate(), buyer, lines));
	}

	/** Isi baslatir (baslamadiysa) ve sonucunu bekler; bitmezse kayit formalizing kalir. */
	private void formalize(AccountingClient client, Document document, Export export, String externalId) {
		LocalDateTime now = Timestamps.now();
		String jobId = export.jobId();
		if (jobId == null) {
			Optional<String> address = document.buyerTaxNumber() == null ? Optional.empty()
				: client.eInvoiceAddress(document.buyerTaxNumber());
			String type = address.isPresent() ? "e_invoice" : "e_archive";
			jobId = address.isPresent() ? client.formalizeEInvoice(externalId, address.get())
				: client.formalizeEArchive(externalId, internetSale(document));
			jdbc.sql("""
				update integration_invoices set status = 'formalizing', e_document_type = :type, job_id = :job,
					error = null, attempted_at = :now, updated_at = :now where id = :id
				""").param("type", type).param("job", jobId).param("now", now).param("id", export.id()).update();
		}
		AccountingClient.Job job = waitFor(client, jobId);
		now = Timestamps.now();
		if (!job.finished()) {
			jdbc.sql("""
				update integration_invoices set status = 'formalizing', attempted_at = :now, updated_at = :now
				where id = :id
				""").param("now", now).param("id", export.id()).update();
			return;
		}
		if (job.failed()) {
			// Is id'si silinir: yeniden deneme resmilestirmeyi bastan baslatir.
			jdbc.sql("""
				update integration_invoices set status = 'failed', job_id = null, error = :error, attempted_at = :now,
					updated_at = :now where id = :id
				""").param("error", "Resmileştirme reddedildi" + (job.errors().isEmpty() ? "."
					: ": " + String.join(" · ", job.errors())))
				.param("now", now).param("id", export.id()).update();
			return;
		}
		Optional<AccountingClient.EDocument> eDocument = client.eDocument(externalId);
		jdbc.sql("""
			update integration_invoices set status = 'formalized', official_number = :number,
				e_document_id = :document, error = null, attempted_at = :now, formalized_at = :now, updated_at = :now
			where id = :id
			""").param("number", eDocument.map(AccountingClient.EDocument::number).orElse(null))
			.param("document", eDocument.map(AccountingClient.EDocument::id).orElse(null)).param("now", now)
			.param("id", export.id()).update();
		events.publishEvent(new InvoiceFormalized(document.id()));
	}

	/**
	 * Pazar yerine bildirilecek fatura: numara, tarih ve varsa PDF. Resmilestiren (faturalari gonderen, resmilestirme
	 * acik) etkin bir muhasebe baglantisi yoksa bizdeki numara, dosyasiz. Varsa fatura resmilesene kadar bos; sonra
	 * resmi numara ve resmi belgenin PDF'i (ilk gerektiginde indirilir, saklanir; indirilemezse dosyasiz).
	 */
	public Optional<Ready> ready(long invoiceId) {
		Optional<Document> found = documents.document(invoiceId);
		if (found.isEmpty() || !"issued".equals(found.get().status())) {
			return Optional.empty();
		}
		Document document = found.get();
		boolean formalizing = jdbc.sql("""
			select count(*) from integration_connections
			where provider = 'parasut' and status = 'active' and invoice_push_enabled and e_document_enabled
			""").query(Long.class).single() > 0;
		if (!formalizing) {
			return Optional.of(new Ready(document.number(), document.issueDate(), null));
		}
		Optional<Formalized> formalized = jdbc.sql("""
			select x.id, x.connection_id, x.official_number, x.e_document_type, x.e_document_id
			from integration_invoices x join integration_connections c on c.id = x.connection_id
			where x.invoice_id = :invoice and x.status = 'formalized' and c.e_document_enabled
			order by x.id limit 1
			""").param("invoice", invoiceId).query(Formalized.class).optional();
		if (formalized.isEmpty()) {
			return Optional.empty();
		}
		Formalized f = formalized.get();
		return Optional.of(new Ready(f.officialNumber() != null ? f.officialNumber() : document.number(),
			document.issueDate(), pdf(f).orElse(null)));
	}

	/** Saklanmis PDF; yoksa Parasut'ten indirilip saklanir. */
	private Optional<byte[]> pdf(Formalized f) {
		Optional<byte[]> stored = jdbc.sql("select pdf from integration_invoices where id = :id and pdf is not null")
			.param("id", f.id()).query(byte[].class).optional();
		if (stored.isPresent() || f.eDocumentId() == null) {
			return stored;
		}
		try {
			AccountingClient client = connections.accounting(connections.connection(f.connectionId()));
			String type = "e_invoice".equals(f.eDocumentType()) ? "e_invoices" : "e_archives";
			Optional<byte[]> pdf = client.pdf(new AccountingClient.EDocument(type, f.eDocumentId(), null));
			pdf.ifPresent(bytes -> jdbc.sql("update integration_invoices set pdf = :pdf, updated_at = :now"
				+ " where id = :id").param("pdf", bytes).param("now", Timestamps.now()).param("id", f.id()).update());
			return pdf;
		} catch (RuntimeException e) {
			log.warn("Fatura PDF'i indirilemedi ({}): {}", f.id(), e.getMessage());
			return Optional.empty();
		}
	}

	private AccountingClient.Job waitFor(AccountingClient client, String jobId) {
		AccountingClient.Job job = client.job(jobId);
		for (int attempt = 1; attempt < JOB_ATTEMPTS && !job.finished(); attempt++) {
			try {
				Thread.sleep(jobWait);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return job;
			}
			job = client.job(jobId);
		}
		return job;
	}

	/** Bizdeki kaydin Parasut'teki karsiligi; yoksa bulunur ya da acilir ve saklanir. */
	private String reference(long connectionId, String kind, long localId, Supplier<String> create) {
		Optional<String> existing = jdbc.sql("""
			select external_id from integration_external_refs
			where connection_id = :connection and kind = :kind and local_id = :local
			""").param("connection", connectionId).param("kind", kind).param("local", localId).query(String.class)
			.optional();
		if (existing.isPresent()) {
			return existing.get();
		}
		String externalId = create.get();
		jdbc.sql("""
			insert into integration_external_refs (connection_id, kind, local_id, external_id, created_at)
			values (:connection, :kind, :local, :external, :now)
			on conflict (connection_id, kind, local_id) do update set external_id = excluded.external_id
			""").param("connection", connectionId).param("kind", kind).param("local", localId)
			.param("external", externalId).param("now", Timestamps.now()).update();
		return externalId;
	}

	/** VKN (10 hane) firma, TCKN ya da bos kisi. */
	private static AccountingClient.Contact buyer(Document d) {
		boolean company = d.buyerTaxNumber() != null && d.buyerTaxNumber().length() == 10;
		return new AccountingClient.Contact(d.buyerName(), company, d.buyerTaxNumber(), d.buyerTaxOffice(),
			d.buyerEmail(), d.buyerPhone(), d.buyerAddress(), d.buyerDistrict(), d.buyerCity());
	}

	/** Site ve pazar yeri siparisinde internet satisi bilgisi; elle girilen sipariste yok. */
	private AccountingClient.InternetSale internetSale(Document d) {
		if ("manual".equals(d.orderSource())) {
			return null;
		}
		Optional<Sale> sale = jdbc.sql("""
			select c.provider, c.base_url from integration_orders io
			join integration_connections c on c.id = io.connection_id
			where io.order_id = :order order by io.id limit 1
			""").param("order", d.orderId()).query(Sale.class).optional();
		String source = d.orderSource();
		String url = switch (source) {
			case "trendyol" -> "https://www.trendyol.com";
			case "hepsiburada" -> "https://www.hepsiburada.com";
			default -> sale.map(Sale::baseUrl).orElse(null);
		};
		if (url == null) {
			return null;
		}
		boolean marketplace = !"woocommerce".equals(source);
		String platform = switch (source) {
			case "trendyol" -> "Trendyol";
			case "hepsiburada" -> "Hepsiburada";
			default -> "Site";
		};
		return new AccountingClient.InternetSale(url, marketplace ? "ODEMEARACISI" : "KREDIKARTI/BANKAKARTI",
			platform, d.orderDate() != null ? d.orderDate() : d.issueDate());
	}

	/** KDV dahil fiyattan KDV haric. */
	private static BigDecimal net(BigDecimal gross, int vatRate) {
		return gross.divide(BigDecimal.ONE.add(BigDecimal.valueOf(vatRate).movePointLeft(2)), 4,
			RoundingMode.HALF_UP);
	}

	private static final String SELECT = """
		select id, connection_id, invoice_id, status, external_id, e_document_type, job_id from integration_invoices
		""";

	private Export export(long connectionId, long invoiceId) {
		return jdbc.sql(SELECT + " where connection_id = :connection and invoice_id = :invoice")
			.param("connection", connectionId).param("invoice", invoiceId).query(Export.class).single();
	}

	private void error(long id, String error, LocalDateTime now) {
		jdbc.sql("update integration_invoices set error = :error, attempted_at = :now, updated_at = :now"
			+ " where id = :id").param("error", error).param("now", now).param("id", id).update();
	}

	private static String message(RuntimeException e) {
		return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
	}

}
