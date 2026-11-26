package com.olivamore.api.integration.order;

import com.olivamore.api.integration.connection.Connections;
import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.sync.SyncRuns;
import com.olivamore.api.sales.ExternalOrderIntake;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Siparis cekme (docs/INTEGRATIONS.md 0): platformun hazirlanmayi bekleyen siparisleri alinir; her siparis bir kez
 * kaydedilir. Kalemlerin hepsi eslesmis urunlere bagliysa siparis moduline taslak olarak girer, degilse "inceleme
 * bekliyor" kalir ve urun eslestirilince (elle "tekrar dene" ya da bir sonraki cekmede) yeniden denenir.
 */
@Service
public class OrderPull {

	/** Ilk cekmede geriye bakilan sure; sonra son basarili cekmeden biraz oncesinden baslanir. */
	static final int FIRST_PULL_DAYS = 7;
	static final int OVERLAP_MINUTES = 10;

	record Pull(int received, int imported, int pending, int skipped) implements SyncRuns.Summary {

		@Override
		public String summary() {
			return received + " yeni sipariş: " + imported + " alındı, " + pending + " inceleme bekliyor"
				+ (skipped > 0 ? " · " + skipped + " daha önce alınmış" : "");
		}

	}

	/** @param customer siparisteki alici adi */
	record IntegrationOrder(long id, String externalId, String externalNumber, String status, Long orderId,
		String problem, String customer, int lineCount, LocalDateTime receivedAt, LocalDateTime importedAt) {
	}

	private record PendingLine(long id, String payload) {
	}

	/** Kayitli siparisin bicimi uygulamanin cevap ayarlarindan bagimsiz: yazilan aynen geri okunur. */
	private static final JsonMapper PAYLOAD = JsonMapper.builder().build();

	private final JdbcClient jdbc;
	private final Connections connections;
	private final SyncRuns runs;
	private final ExternalOrderIntake intake;
	private final TransactionTemplate transaction;
	private final ZoneId zone;

	OrderPull(JdbcClient jdbc, Connections connections, SyncRuns runs, ExternalOrderIntake intake,
		TransactionTemplate transaction,
		@Value("${olive.integration.zone:Europe/Istanbul}") String zone) {
		this.jdbc = jdbc;
		this.connections = connections;
		this.runs = runs;
		this.intake = intake;
		this.transaction = transaction;
		this.zone = ZoneId.of(zone);
	}

	/** Bekleyenleri yeniden dener, sonra yeni siparisleri ceker; calisma senkron gecmisine yazilir. */
	public Pull pull(long connectionId, SyncRuns.Trigger trigger) {
		Connections.Connection connection = connections.connection(connectionId);
		if (!connection.orderPullEnabled()) {
			throw new BusinessRuleException("Bu bağlantıda sipariş çekme kapalı.");
		}
		LocalDateTime since = connection.lastOrderSyncAt() == null
			? Timestamps.now().minusDays(FIRST_PULL_DAYS)
			: connection.lastOrderSyncAt().minusMinutes(OVERLAP_MINUTES);
		return runs.record(connectionId, SyncRuns.Kind.ORDERS, trigger, () -> {
			Pull retried = retryPending(connectionId);
			List<ExternalOrder> orders = connections.client(connection).orders(since);
			int received = 0;
			int imported = retried.imported();
			int pending = 0;
			int skipped = 0;
			for (ExternalOrder order : orders) {
				Optional<Boolean> result = transaction.execute(status -> receive(connectionId, order));
				if (result == null || result.isEmpty()) {
					skipped++;
				} else {
					received++;
					if (result.get()) {
						imported++;
					} else {
						pending++;
					}
				}
			}
			return new Pull(received, imported, pending + retried.pending(), skipped);
		});
	}

	/** Inceleme bekleyen siparisleri yeniden dener (urun eslestirildikten sonra). */
	public Pull retry(long connectionId) {
		connections.connection(connectionId);
		return retryPending(connectionId);
	}

	@Transactional(readOnly = true)
	List<IntegrationOrder> list(long connectionId, String status) {
		connections.connection(connectionId);
		return jdbc.sql("""
			select id, external_id, external_number, status, order_id, problem,
				payload -> 'customer' ->> 'name' as customer, jsonb_array_length(payload -> 'lines') as line_count,
				received_at, imported_at
			from integration_orders
			where connection_id = :connection and (cast(:status as varchar) is null or status = :status)
			order by received_at desc, id desc limit 200
			""").param("connection", connectionId).param("status", status).query(IntegrationOrder.class).list();
	}

	private Pull retryPending(long connectionId) {
		List<PendingLine> pending = jdbc.sql("""
			select id, payload::text as payload from integration_orders
			where connection_id = :connection and status = 'pending' order by id
			""").param("connection", connectionId).query(PendingLine.class).list();
		int imported = 0;
		for (PendingLine line : pending) {
			ExternalOrder order = PAYLOAD.readValue(line.payload(), ExternalOrder.class);
			Boolean done = transaction.execute(status -> tryImport(connectionId, line.id(), order));
			if (Boolean.TRUE.equals(done)) {
				imported++;
			}
		}
		return new Pull(0, imported, pending.size() - imported, 0);
	}

	/** Yeni siparisi kaydeder ve almayi dener; daha once geldiyse bos. */
	private Optional<Boolean> receive(long connectionId, ExternalOrder order) {
		LocalDateTime now = Timestamps.now();
		Optional<Long> id = jdbc.sql("""
			insert into integration_orders (connection_id, external_id, external_number, status, payload, received_at,
				created_at, updated_at)
			values (:connection, :external, :number, 'pending', cast(:payload as jsonb), :now, :now, :now)
			on conflict (connection_id, external_id) do nothing
			returning id
			""").param("connection", connectionId).param("external", order.externalId())
			.param("number", order.number()).param("payload", PAYLOAD.writeValueAsString(order)).param("now", now)
			.query(Long.class).optional();
		return id.map(rowId -> tryImport(connectionId, rowId, order));
	}

	/** Kalemler eslesmisse siparisi alir (true); eslesmeyen varsa gerekceyle bekletir (false). */
	private boolean tryImport(long connectionId, long rowId, ExternalOrder order) {
		List<ExternalOrderIntake.Item> items = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (ExternalOrder.Line line : order.lines()) {
			Optional<Long> product = jdbc.sql("""
				select finished_product_id from integration_product_links
				where connection_id = :connection and external_product_id = :product
					and external_variant_id = :variant and finished_product_id is not null
				""").param("connection", connectionId).param("product", line.productId())
				.param("variant", line.variantId() == null ? "" : line.variantId()).query(Long.class).optional();
			if (product.isEmpty()) {
				problems.add("Eşleşmeyen ürün: " + (line.name() == null ? line.productId() : line.name()));
			} else if (line.quantity() <= 0) {
				problems.add("Geçersiz adet: " + line.name());
			} else {
				items.add(new ExternalOrderIntake.Item(product.get(), line.quantity()));
			}
		}
		if (order.lines().isEmpty()) {
			problems.add("Siparişte kalem yok.");
		}
		LocalDateTime now = Timestamps.now();
		if (!problems.isEmpty()) {
			jdbc.sql("update integration_orders set problem = :problem, updated_at = :now where id = :id")
				.param("problem", String.join(" · ", problems)).param("now", now).param("id", rowId).update();
			return false;
		}
		long orderId = intake.receive(new ExternalOrderIntake.IncomingOrder(
			connections.connection(connectionId).provider().key(), order.number() == null ? order.externalId()
				: order.number(), orderDate(order), new ExternalOrderIntake.Buyer(order.customer().name(),
				order.customer().company(), order.customer().email(), order.customer().phone()),
			new ExternalOrderIntake.Delivery(order.address().country(), order.address().city(),
				order.address().district(), order.address().line(), order.address().postalCode()), items,
			order.note()));
		jdbc.sql("""
			update integration_orders set status = 'imported', order_id = :order, problem = null, imported_at = :now,
				updated_at = :now where id = :id
			""").param("order", orderId).param("now", now).param("id", rowId).update();
		return true;
	}

	/** Siparis tarihi fabrikanin yerel gunu. */
	private LocalDate orderDate(ExternalOrder order) {
		LocalDateTime created = order.createdAt() == null ? Timestamps.now() : order.createdAt();
		return created.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate();
	}

}
