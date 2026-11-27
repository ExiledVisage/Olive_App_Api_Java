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

/**
 * "Tedarik edilemedi" bildirimi (docs/INTEGRATIONS.md 2): pazar yerinden gelen siparis bizde iptal edilince paketin
 * butun kalemleri platforma tedarik edilemedi ("stok tukendi") olarak bildirilir; Trendyol paketi iptal eder ve
 * musteriye para iadesi yapar. Hata kayda yazilir, zamanlayici yarim saatte bir yeniden dener.
 */
@Service
public class UnsuppliedReport {

	private static final Logger log = LoggerFactory.getLogger(UnsuppliedReport.class);

	private record Target(long id, long connectionId, String payload) {
	}

	private static final JsonMapper PAYLOAD = JsonMapper.builder().build();

	private static final String SELECT = """
		select io.id, io.connection_id, io.payload::text as payload
		from integration_orders io
		join orders o on o.id = io.order_id
		join integration_connections c on c.id = io.connection_id
		where io.status = 'imported' and o.status = 'cancelled' and c.status = 'active'
			and c.provider in (:providers) and io.platform_status is distinct from 'unsupplied'
		""";

	private final JdbcClient jdbc;
	private final Connections connections;

	UnsuppliedReport(JdbcClient jdbc, Connections connections) {
		this.jdbc = jdbc;
		this.connections = connections;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onStatusChanged(SalesOrderStatusChanged event) {
		if (!"cancelled".equals(event.to())) {
			return;
		}
		jdbc.sql(SELECT + " and io.order_id = :order").param("providers", providers())
			.param("order", event.orderId()).query(Target.class).list().forEach(this::report);
	}

	/** @return bildirilen siparis sayisi */
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

	private boolean report(Target target) {
		LocalDateTime now = Timestamps.now();
		try {
			ExternalOrder order = PAYLOAD.readValue(target.payload(), ExternalOrder.class);
			connections.client(connections.connection(target.connectionId())).reportUnsupplied(order);
			jdbc.sql("""
				update integration_orders set platform_status = 'unsupplied', platform_error = null,
					platform_attempted_at = :now, updated_at = :now where id = :id
				""").param("now", now).param("id", target.id()).update();
			return true;
		} catch (RuntimeException e) {
			log.warn("Entegrasyon siparisi {} tedarik edilemedi bildirilemedi: {}", target.id(), e.getMessage());
			jdbc.sql("""
				update integration_orders set platform_error = :error, platform_attempted_at = :now, updated_at = :now
				where id = :id
				""").param("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
				.param("now", now).param("id", target.id()).update();
			return false;
		}
	}

	private static List<String> providers() {
		return Arrays.stream(Provider.values()).filter(Provider::reportsUnsupplied).map(Provider::key).toList();
	}

}
