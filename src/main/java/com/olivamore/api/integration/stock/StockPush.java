package com.olivamore.api.integration.stock;

import com.olivamore.api.integration.connection.Connections;
import com.olivamore.api.integration.provider.StockUpdate;
import com.olivamore.api.integration.sync.SyncRuns;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stok gonderimi (docs/INTEGRATIONS.md 0). Platforma giden adet satilabilir stok: fabrika stogu (raf ve acikta)
 * eksi acik siparislerdeki adetler (taslaktan hazira; sevk edilen zaten stoktan duser, iptal sayilmaz). Eslesmis her
 * urun icin en son gonderilen adet tutulur; satilabilir adet ondan farkliysa gonderilir. Basarisiz gonderimde kayitli
 * adet degismez, sonraki calismada yeniden denenir. Ilk calismada platformdaki adet zaten dogruysa gonderilmez.
 */
@Service
public class StockPush {

	/** Satilabilir stoktan dusen siparis durumlari. */
	static final List<String> OPEN_ORDER_STATUSES = List.of("draft", "confirmed", "stock_check", "in_production",
		"ready");

	/** @param needsPush satilabilir adet son gonderilenden (ilk seferde platformdakinden) farkli */
	record Line(long linkId, String externalProductId, String externalVariantId, String externalName,
		long finishedProductId, String productName, int totalStock, int committed, int sellable,
		Integer externalStock, Integer pushedStock, LocalDateTime pushedAt, String pushError, boolean needsPush) {
	}

	record Push(int checked, int updated, int failed, int unchanged) implements SyncRuns.Summary {

		@Override
		public String summary() {
			return checked + " ürün kontrol edildi: " + updated + " güncellendi"
				+ (failed > 0 ? ", " + failed + " hata" : "") + (unchanged > 0 ? ", " + unchanged + " zaten doğru" : "");
		}

	}

	private record LineRow(long linkId, String externalProductId, String externalVariantId, String externalName,
		long finishedProductId, String productName, int totalStock, int committed, Integer externalStock,
		Integer pushedStock, LocalDateTime pushedAt, String pushError) {

		Line line() {
			int sellable = Math.max(0, totalStock - committed);
			Integer reference = pushedStock != null ? pushedStock : externalStock;
			return new Line(linkId, externalProductId, externalVariantId, externalName, finishedProductId,
				productName, totalStock, committed, sellable, externalStock, pushedStock, pushedAt, pushError,
				reference == null || reference != sellable);
		}

	}

	private final JdbcClient jdbc;
	private final Connections connections;
	private final SyncRuns runs;
	private final TransactionTemplate transaction;

	StockPush(JdbcClient jdbc, Connections connections, SyncRuns runs, TransactionTemplate transaction) {
		this.jdbc = jdbc;
		this.connections = connections;
		this.runs = runs;
		this.transaction = transaction;
	}

	/** Eslesmis urunlerin satilabilir stogu ve gonderilecek mi; gonderim kapali da olsa bakilabilir. */
	@Transactional(readOnly = true)
	List<Line> preview(long connectionId) {
		connections.connection(connectionId);
		return lines(connectionId);
	}

	/** Farkli olanlari gonderir; calisma senkron gecmisine yazilir. Gonderim kapaliysa reddedilir. */
	public Push push(long connectionId, SyncRuns.Trigger trigger) {
		Connections.Connection connection = connections.connection(connectionId);
		if (!connection.stockPushEnabled()) {
			throw new BusinessRuleException("Bu bağlantıda stok gönderimi kapalı. Eşleştirmeyi kontrol edip "
				+ "bağlantı ayarından açın.");
		}
		return runs.record(connectionId, SyncRuns.Kind.STOCK, trigger, () -> {
			List<Line> lines = transaction.execute(status -> lines(connectionId));
			List<Line> changed = lines.stream().filter(Line::needsPush).toList();
			int unchanged = lines.size() - changed.size();
			// Platformdaki adet zaten dogru olan satir gonderilmeden "gonderildi" sayilir.
			transaction.executeWithoutResult(status -> lines.stream().filter(l -> !l.needsPush()
				&& l.pushedStock() == null).forEach(l -> markPushed(l.linkId(), l.sellable())));
			if (changed.isEmpty()) {
				return new Push(lines.size(), 0, 0, unchanged);
			}
			List<StockUpdate.Result> results = connections.client(connection).pushStock(changed.stream()
				.map(l -> new StockUpdate(l.externalProductId(), l.externalVariantId(), l.sellable())).toList());
			int failed = 0;
			for (int i = 0; i < changed.size(); i++) {
				Line line = changed.get(i);
				StockUpdate.Result result = results.get(i);
				if (result.ok()) {
					transaction.executeWithoutResult(status -> markPushed(line.linkId(), line.sellable()));
				} else {
					failed++;
					transaction.executeWithoutResult(status -> markFailed(line.linkId(), result.error()));
				}
			}
			return new Push(lines.size(), changed.size() - failed, failed, unchanged);
		});
	}

	private List<Line> lines(long connectionId) {
		return jdbc.sql("""
			select l.id as link_id, l.external_product_id, l.external_variant_id, l.external_name,
				p.id as finished_product_id, p.name as product_name, p.total_stock,
				coalesce((select sum(i.quantity) from order_items i join orders o on o.id = i.order_id
					where i.finished_product_id = p.id and o.status in (:open)), 0) as committed,
				l.external_stock, l.pushed_stock, l.pushed_at, l.push_error
			from integration_product_links l join finished_products p on p.id = l.finished_product_id
			where l.connection_id = :connection
			order by l.external_name, l.id
			""").param("connection", connectionId).param("open", OPEN_ORDER_STATUSES).query(LineRow.class).list()
			.stream().map(LineRow::line).toList();
	}

	private void markPushed(long linkId, int quantity) {
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			update integration_product_links set pushed_stock = :quantity, external_stock = :quantity,
				pushed_at = :now, push_error = null, updated_at = :now where id = :id
			""").param("quantity", quantity).param("now", now).param("id", linkId).update();
	}

	private void markFailed(long linkId, String error) {
		jdbc.sql("update integration_product_links set push_error = :error, updated_at = :now where id = :id")
			.param("error", error).param("now", Timestamps.now()).param("id", linkId).update();
	}

}
