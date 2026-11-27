package com.olivamore.api.integration.order;

import com.olivamore.api.integration.connection.Connections;
import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.Provider;
import com.olivamore.api.sales.SalesOrderStatusChanged;
import com.olivamore.api.shared.time.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Hazirlaniyor" bildirimi (docs/INTEGRATIONS.md 2): pazar yerinden gelen siparis bizde onaylanip hazirlanmaya
 * gecince platforma bildirilir (Trendyol paketi Picking). Durum degisikligi kaydedildikten sonra hemen denenir;
 * platform reddeder ya da ulasilamazsa hata siparisin kaydina yazilir, zamanlayici yarim saatte bir yeniden dener.
 * Bildirimin hatasi bizdeki durum degisikligini geri almaz.
 */
@Service
public class PreparingReport {

	private static final Logger log = LoggerFactory.getLogger(PreparingReport.class);

	/** Bu durumlardaki siparis hazirlaniyor sayilir; sevk ya da iptalden sonra bildirilmez. */
	static final Set<String> PREPARING = Set.of("confirmed", "stock_check", "in_production", "ready");

	/** Reddedilen bildirimin yeniden denenmesi icin bekleme. */
	static final int RETRY_MINUTES = 30;

	private record Target(long id, long connectionId, String payload) {
	}

	private static final JsonMapper PAYLOAD = JsonMapper.builder().build();

	private static final String SELECT = """
		select io.id, io.connection_id, io.payload::text as payload
		from integration_orders io
		join orders o on o.id = io.order_id
		join integration_connections c on c.id = io.connection_id
		where io.status = 'imported' and io.platform_status is null and c.status = 'active'
			and o.status in (:preparing) and c.provider in (:providers)
		""";

	private final JdbcClient jdbc;
	private final Connections connections;

	PreparingReport(JdbcClient jdbc, Connections connections) {
		this.jdbc = jdbc;
		this.connections = connections;
	}

	/** Siparis hazirlanmaya gecti: kaydedildikten sonra bildirilir. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onStatusChanged(SalesOrderStatusChanged event) {
		if (!PREPARING.contains(event.to())) {
			return;
		}
		List<Target> targets = jdbc.sql(SELECT + " and io.order_id = :order").params(scope())
			.param("order", event.orderId()).query(Target.class).list();
		targets.forEach(this::report);
	}

	/**
	 * Bildirilmemis siparisler: hic denenmemis ya da son denemesi {@value #RETRY_MINUTES} dakikadan eski olanlar
	 * (zamanlayici). {@code now} ise bekleme yok (elle "tekrar dene").
	 *
	 * @return bildirilen siparis sayisi
	 */
	public int retry(long connectionId, boolean now) {
		List<Target> targets = jdbc.sql(SELECT + """
			 and io.connection_id = :connection
			 and (io.platform_attempted_at is null or io.platform_attempted_at < :before)
			order by io.id
			""").params(scope()).param("connection", connectionId)
			.param("before", Timestamps.now().plusMinutes(now ? 1 : -RETRY_MINUTES)).query(Target.class).list();
		return (int) targets.stream().filter(this::report).count();
	}

	private boolean report(Target target) {
		LocalDateTime now = Timestamps.now();
		try {
			ExternalOrder order = PAYLOAD.readValue(target.payload(), ExternalOrder.class);
			Connections.Connection connection = connections.connection(target.connectionId());
			String ref = connections.client(connection).reportPreparing(order);
			jdbc.sql("""
				update integration_orders set platform_status = 'preparing', platform_ref = :ref, platform_error = null,
					platform_attempted_at = :now, updated_at = :now where id = :id
				""").param("ref", ref).param("now", now).param("id", target.id()).update();
			return true;
		} catch (RuntimeException e) {
			log.warn("Entegrasyon siparisi {} hazirlaniyor bildirilemedi: {}", target.id(), e.getMessage());
			jdbc.sql("""
				update integration_orders set platform_error = :error, platform_attempted_at = :now, updated_at = :now
				where id = :id
				""").param("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
				.param("now", now).param("id", target.id()).update();
			return false;
		}
	}

	private static Map<String, Object> scope() {
		return Map.of("preparing", List.copyOf(PREPARING), "providers",
			Arrays.stream(Provider.values()).filter(Provider::reportsPreparing).map(Provider::key).toList());
	}

}
