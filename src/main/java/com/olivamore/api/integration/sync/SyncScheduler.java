package com.olivamore.api.integration.sync;

import com.olivamore.api.integration.accounting.InvoiceExport;
import com.olivamore.api.integration.order.OrderPull;
import com.olivamore.api.integration.order.PreparingReport;
import com.olivamore.api.integration.stock.StockPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Zamanlanmis senkron (docs/INTEGRATIONS.md 0): aktif her baglantida, aciksa siparis cekme ve stok gonderimi;
 * pazar yerinde bildirilemeyen "hazirlaniyor" durumlari yeniden denenir.
 * Bir baglantinin hatasi digerlerini durdurmaz; hata senkron gecmisine ve baglantiya yazilir. Testlerde kapali
 * ({@code olive.integration.schedule.enabled=false}).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "olive.integration.schedule.enabled", havingValue = "true", matchIfMissing = true)
class SyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

	private record Target(long id, boolean orderPullEnabled, boolean stockPushEnabled) {
	}

	private final JdbcClient jdbc;
	private final OrderPull orders;
	private final StockPush stock;
	private final PreparingReport preparing;
	private final InvoiceExport invoices;

	SyncScheduler(JdbcClient jdbc, OrderPull orders, StockPush stock, PreparingReport preparing,
		InvoiceExport invoices) {
		this.jdbc = jdbc;
		this.orders = orders;
		this.stock = stock;
		this.preparing = preparing;
		this.invoices = invoices;
	}

	@Scheduled(initialDelayString = "${olive.integration.schedule.initial-delay:PT1M}",
		fixedDelayString = "${olive.integration.schedule.interval:PT5M}")
	void run() {
		List<Target> targets = jdbc.sql("""
			select id, order_pull_enabled, stock_push_enabled from integration_connections
			where status = 'active' and provider in ('woocommerce', 'trendyol', 'hepsiburada') order by id
			""").query(Target.class).list();
		for (Target target : targets) {
			if (target.orderPullEnabled()) {
				safely(target.id(), "sipariş", () -> orders.pull(target.id(), SyncRuns.Trigger.SCHEDULE));
			}
			safely(target.id(), "bildirim", () -> preparing.retry(target.id(), false));
			if (target.stockPushEnabled()) {
				safely(target.id(), "stok", () -> stock.push(target.id(), SyncRuns.Trigger.SCHEDULE));
			}
		}
		List<Long> accounting = jdbc.sql(
			"select id from integration_connections where status = 'active' and provider = 'parasut'"
				+ " and invoice_push_enabled order by id").query(Long.class).list();
		for (long id : accounting) {
			safely(id, "fatura", () -> invoices.retry(id, false));
		}
	}

	private static void safely(long connectionId, String what, Runnable work) {
		try {
			work.run();
		} catch (RuntimeException e) {
			log.warn("Entegrasyon {} {} senkronu basarisiz: {}", connectionId, what, e.getMessage());
		}
	}

}
