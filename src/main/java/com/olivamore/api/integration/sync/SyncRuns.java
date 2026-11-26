package com.olivamore.api.integration.sync;

import com.olivamore.api.shared.time.Timestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Senkron gecmisi (docs/INTEGRATIONS.md 0): katalog, siparis ve stok islerinin her calismasi kaydedilir; basarisiz
 * calisma baglantinin son hatasina da yazilir. Kayit isin kendi transaction'indan bagimsizdir: is geri alinsa da
 * gecmiste kalir.
 */
@Service
public class SyncRuns {

	public enum Kind { CATALOG, ORDERS, STOCK }

	public enum Trigger { MANUAL, SCHEDULE }

	record Run(long id, String kind, String triggeredBy, LocalDateTime startedAt, LocalDateTime finishedAt, Boolean ok,
		String summary, String error) {
	}

	private final JdbcClient jdbc;
	private final TransactionTemplate separate;

	SyncRuns(JdbcClient jdbc, PlatformTransactionManager transactions) {
		this.jdbc = jdbc;
		this.separate = new TransactionTemplate(transactions);
		this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Isi calistirir ve sonucunu gecmise yazar; isin dondurdugu metin ozet olur. Hata gecmise ve baglantiya yazilip
	 * yeniden firlatilir.
	 */
	public <T extends Summary> T record(long connectionId, Kind kind, Trigger trigger, Supplier<T> work) {
		long id = start(connectionId, kind, trigger);
		try {
			T result = work.get();
			finish(id, connectionId, kind, true, result.summary(), null);
			return result;
		} catch (RuntimeException e) {
			finish(id, connectionId, kind, false, null, e.getMessage());
			throw e;
		}
	}

	/** Gecmise yazilacak ozet ("12 urun, 9 eslesti"). */
	public interface Summary {

		String summary();

	}

	@Transactional(readOnly = true)
	List<Run> recent(long connectionId, int limit) {
		return jdbc.sql("""
			select id, kind, triggered_by, started_at, finished_at, ok, summary, error from integration_sync_runs
			where connection_id = :connection order by started_at desc, id desc limit :limit
			""").param("connection", connectionId).param("limit", limit).query(Run.class).list();
	}

	private long start(long connectionId, Kind kind, Trigger trigger) {
		return separate.execute(status -> jdbc.sql("""
			insert into integration_sync_runs (connection_id, kind, triggered_by, started_at)
			values (:connection, :kind, :trigger, :now) returning id
			""").param("connection", connectionId).param("kind", kind.name().toLowerCase())
			.param("trigger", trigger.name().toLowerCase()).param("now", Timestamps.now()).query(Long.class).single());
	}

	private void finish(long id, long connectionId, Kind kind, boolean ok, String summary, String error) {
		separate.executeWithoutResult(status -> write(id, connectionId, kind, ok, summary, error));
	}

	private void write(long id, long connectionId, Kind kind, boolean ok, String summary, String error) {
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			update integration_sync_runs set finished_at = :now, ok = :ok, summary = :summary, error = :error
			where id = :id
			""").param("now", now).param("ok", ok).param("summary", summary).param("error", error).param("id", id)
			.update();
		if (ok) {
			String column = switch (kind) {
				case ORDERS -> "last_order_sync_at";
				case STOCK -> "last_stock_sync_at";
				case CATALOG -> null;
			};
			jdbc.sql("update integration_connections set last_error = null, last_error_at = null"
				+ (column == null ? "" : ", " + column + " = :now") + " where id = :id")
				.param("now", now).param("id", connectionId).update();
		} else {
			jdbc.sql("update integration_connections set last_error = :error, last_error_at = :now where id = :id")
				.param("error", error).param("now", now).param("id", connectionId).update();
		}
	}

}
