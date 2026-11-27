package com.olivamore.api.integration.order;

import com.olivamore.api.integration.accounting.InvoiceExport;
import com.olivamore.api.integration.connection.Connections;
import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.Provider;
import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.sales.SalesInvoiceIssued;
import com.olivamore.api.shared.time.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Faturanin pazar yerine bildirimi (docs/INVOICES.md): "hazirlaniyor" bildirilmis pazar yeri siparisinin faturasi
 * kesilince paket faturalandi olarak fatura numarasiyla bildirilir (invoiced), resmi belgenin PDF'i varsa dosya da
 * yuklenir (invoice_sent). Resmilestiren muhasebe baglantisi varsa fatura resmilesene kadar beklenir ve resmi numara
 * gider; yoksa bizdeki numara, dosyasiz. Hata siparisin kaydina yazilir, zamanlayici yarim saatte bir yeniden dener.
 */
@Service
public class InvoiceReport {

	private static final Logger log = LoggerFactory.getLogger(InvoiceReport.class);

	private record Target(long id, long connectionId, String payload, String platformStatus, long invoiceId) {
	}

	private static final JsonMapper PAYLOAD = JsonMapper.builder().build();

	private static final String SELECT = """
		select io.id, io.connection_id, io.payload::text as payload, io.platform_status, i.id as invoice_id
		from integration_orders io
		join invoices i on i.order_id = io.order_id and i.status = 'issued'
		join integration_connections c on c.id = io.connection_id
		where io.status = 'imported' and c.status = 'active' and c.provider in (:providers)
			and io.platform_status in ('preparing', 'invoiced')
		""";

	private final JdbcClient jdbc;
	private final Connections connections;
	private final InvoiceExport invoices;

	InvoiceReport(JdbcClient jdbc, Connections connections, InvoiceExport invoices) {
		this.jdbc = jdbc;
		this.connections = connections;
		this.invoices = invoices;
	}

	/** Fatura kesildi (muhasebe gonderiminden sonra calisir: resmilestirme beklenmiyorsa hemen bildirilir). */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onIssued(SalesInvoiceIssued event) {
		forInvoice(event.invoiceId());
	}

	/** Fatura resmilesti: resmi numara ve dosya gider. */
	@EventListener
	void onFormalized(InvoiceExport.InvoiceFormalized event) {
		forInvoice(event.invoiceId());
	}

	/**
	 * Faturasi bildirilmemis ya da dosyasi yuklenmemis siparisler: hic denenmemis ya da son denemesi
	 * {@value PreparingReport#RETRY_MINUTES} dakikadan eski olanlar; {@code now} ise bekleme yok.
	 *
	 * @return bildirimi ilerleyen siparis sayisi
	 */
	public int retry(long connectionId, boolean now) {
		List<Target> targets = jdbc.sql(SELECT + """
			 and io.connection_id = :connection
			 and (io.platform_attempted_at is null or io.platform_attempted_at < :before)
			order by io.id
			""").param("providers", providers()).param("connection", connectionId)
			.param("before", Timestamps.now().plusMinutes(now ? 1 : -PreparingReport.RETRY_MINUTES))
			.query(Target.class).list();
		return (int) targets.stream().filter(this::report).count();
	}

	private void forInvoice(long invoiceId) {
		jdbc.sql(SELECT + " and i.id = :invoice").param("providers", providers()).param("invoice", invoiceId)
			.query(Target.class).list().forEach(this::report);
	}

	/** Ilerlediyse true; fatura hazir degilse ya da yapacak is yoksa dokunmadan false. */
	private boolean report(Target target) {
		Optional<InvoiceExport.Ready> ready = invoices.ready(target.invoiceId());
		boolean invoiced = "invoiced".equals(target.platformStatus());
		if (ready.isEmpty() || (invoiced && ready.get().pdf() == null)) {
			return false;
		}
		InvoiceExport.Ready invoice = ready.get();
		LocalDateTime now = Timestamps.now();
		try {
			ExternalOrder order = PAYLOAD.readValue(target.payload(), ExternalOrder.class);
			ProviderClient client = connections.client(connections.connection(target.connectionId()));
			if (!invoiced) {
				client.reportInvoiced(order, invoice.number());
				status(target.id(), "invoiced", now);
			}
			if (invoice.pdf() != null) {
				client.sendInvoiceFile(order, invoice.number(), invoice.issueDate(), invoice.pdf());
				status(target.id(), "invoice_sent", now);
			}
			return true;
		} catch (RuntimeException e) {
			log.warn("Entegrasyon siparisi {} faturasi bildirilemedi: {}", target.id(), e.getMessage());
			jdbc.sql("""
				update integration_orders set platform_error = :error, platform_attempted_at = :now, updated_at = :now
				where id = :id
				""").param("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
				.param("now", now).param("id", target.id()).update();
			return false;
		}
	}

	private void status(long id, String status, LocalDateTime now) {
		jdbc.sql("""
			update integration_orders set platform_status = :status, platform_error = null,
				platform_attempted_at = :now, updated_at = :now where id = :id
			""").param("status", status).param("now", now).param("id", id).update();
	}

	private static List<String> providers() {
		return Arrays.stream(Provider.values()).filter(Provider::reportsInvoice).map(Provider::key).toList();
	}

}
