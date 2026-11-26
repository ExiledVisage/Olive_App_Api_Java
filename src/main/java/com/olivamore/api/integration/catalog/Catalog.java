package com.olivamore.api.integration.catalog;

import com.olivamore.api.integration.connection.Connections;
import com.olivamore.api.integration.provider.ExternalProduct;
import com.olivamore.api.integration.sync.SyncRuns;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Urun eslestirme (docs/INTEGRATIONS.md 0): platformdaki urunler cekilir, her biri bir satir olur; barkod, yoksa SKU
 * bizim urunle tutarsa kendiliginden baglanir. Elle baglanan ya da bilerek eslestirilmeyen ("ignored") satira
 * kendiliginden dokunulmaz. Platformdan kalkan urunun satiri silinir.
 */
@Service
public class Catalog {

	/** @param status matched (bagli), unmatched (bagsiz), ignored (bilerek eslestirilmedi) */
	record Link(long id, String externalProductId, String externalVariantId, String externalName, String externalSku,
		String externalBarcode, Integer externalStock, String status, String matchSource, Product product,
		LocalDateTime lastSeenAt) {
	}

	record Product(long id, String name, String barcode, String sku, int totalStock) {
	}

	record Refresh(int total, int matched, int matchedNow, int unmatched, int removed) implements SyncRuns.Summary {

		@Override
		public String summary() {
			return total + " ürün, " + matched + " eşleşti (" + matchedNow + " yeni), " + unmatched + " eşleşmedi"
				+ (removed > 0 ? ", " + removed + " sitede yok, kaldırıldı" : "");
		}

	}

	/** @param finishedProductId bos ise satir "eslestirilmesin" olarak isaretlenir */
	record LinkRequest(Long finishedProductId) {
	}

	private record LinkLine(long id, String externalProductId, String externalVariantId, String externalName,
		String externalSku, String externalBarcode, Integer externalStock, Long finishedProductId, String matchSource,
		LocalDateTime lastSeenAt, String productName, String productBarcode, String productSku, Integer productStock) {

		Link link() {
			String status = finishedProductId != null ? "matched" : "ignored".equals(matchSource) ? "ignored"
				: "unmatched";
			return new Link(id, externalProductId, externalVariantId, externalName, externalSku, externalBarcode,
				externalStock, status, matchSource, finishedProductId == null ? null
					: new Product(finishedProductId, productName, productBarcode, productSku,
						productStock == null ? 0 : productStock), lastSeenAt);
		}

	}

	private static final String SELECT = """
		select l.id, l.external_product_id, l.external_variant_id, l.external_name, l.external_sku,
			l.external_barcode, l.external_stock, l.finished_product_id, l.match_source, l.last_seen_at,
			p.name as product_name, p.barcode as product_barcode, p.sku as product_sku, p.total_stock as product_stock
		from integration_product_links l left join finished_products p on p.id = l.finished_product_id
		""";

	private final JdbcClient jdbc;
	private final Connections connections;
	private final SyncRuns runs;
	private final TransactionTemplate transaction;

	Catalog(JdbcClient jdbc, Connections connections, SyncRuns runs, TransactionTemplate transaction) {
		this.jdbc = jdbc;
		this.connections = connections;
		this.runs = runs;
		this.transaction = transaction;
	}

	/**
	 * Platformdaki urunleri ceker (istek transaction disinda), satirlari gunceller ve kendiliginden eslestirir;
	 * calisma senkron gecmisine yazilir.
	 */
	public Refresh refresh(long connectionId, SyncRuns.Trigger trigger) {
		Connections.Connection connection = connections.connection(connectionId);
		return runs.record(connectionId, SyncRuns.Kind.CATALOG, trigger, () -> {
			List<ExternalProduct> products = connections.client(connection).products();
			return transaction.execute(status -> apply(connectionId, products));
		});
	}

	private Refresh apply(long connectionId, List<ExternalProduct> products) {
		LocalDateTime now = Timestamps.now();
		for (ExternalProduct p : products) {
			jdbc.sql("""
				insert into integration_product_links (connection_id, external_product_id, external_variant_id,
					external_name, external_sku, external_barcode, external_stock, last_seen_at, created_at, updated_at)
				values (:connection, :product, :variant, :name, :sku, :barcode, :stock, :now, :now, :now)
				on conflict (connection_id, external_product_id, external_variant_id) do update set
					external_name = excluded.external_name, external_sku = excluded.external_sku,
					external_barcode = excluded.external_barcode, external_stock = excluded.external_stock,
					last_seen_at = excluded.last_seen_at, updated_at = excluded.updated_at
				""").param("connection", connectionId).param("product", p.productId())
				.param("variant", p.variantId() == null ? "" : p.variantId())
				.param("name", p.name() == null ? "Ürün #" + p.productId() : cut(p.name(), 255))
				.param("sku", cut(p.sku(), 100)).param("barcode", cut(p.barcode(), 64)).param("stock", p.stock())
				.param("now", now).update();
		}
		// Bu calismada gorulmeyen (siteden kalkan) urunlerin satirlari silinir.
		Set<String> seen = products.stream()
			.map(p -> p.productId() + "/" + (p.variantId() == null ? "" : p.variantId())).collect(Collectors.toSet());
		List<Long> gone = jdbc.sql("""
			select id, external_product_id || '/' || external_variant_id as external_key
			from integration_product_links where connection_id = :connection
			""").param("connection", connectionId)
			.query((rs, i) -> seen.contains(rs.getString("external_key")) ? null : rs.getLong("id")).list().stream()
			.filter(Objects::nonNull).toList();
		int removed = gone.isEmpty() ? 0 : jdbc.sql("delete from integration_product_links where id in (:ids)")
			.param("ids", gone).update();
		int byBarcode = jdbc.sql("""
			update integration_product_links l set finished_product_id = p.id, match_source = 'barcode',
				updated_at = :now
			from finished_products p
			where l.connection_id = :connection and l.finished_product_id is null
				and coalesce(l.match_source, '') <> 'ignored'
				and l.external_barcode is not null and p.barcode = l.external_barcode
			""").param("connection", connectionId).param("now", now).update();
		int bySku = jdbc.sql("""
			update integration_product_links l set finished_product_id = p.id, match_source = 'sku', updated_at = :now
			from finished_products p
			where l.connection_id = :connection and l.finished_product_id is null
				and coalesce(l.match_source, '') <> 'ignored'
				and l.external_sku is not null and lower(p.sku) = lower(l.external_sku)
			""").param("connection", connectionId).param("now", now).update();
		Map<Boolean, Long> counts = jdbc.sql("""
			select finished_product_id is not null as matched from integration_product_links
			where connection_id = :connection
			""").param("connection", connectionId).query(Boolean.class).list().stream()
			.collect(Collectors.groupingBy(b -> b, Collectors.counting()));
		int matched = counts.getOrDefault(true, 0L).intValue();
		return new Refresh(products.size(), matched, byBarcode + bySku, counts.getOrDefault(false, 0L).intValue(),
			removed);
	}

	/** {@code status}: matched, unmatched, ignored ya da hepsi. */
	@Transactional(readOnly = true)
	List<Link> links(long connectionId, String status) {
		connections.connection(connectionId);
		String filter = switch (status == null ? "" : status) {
			case "matched" -> " and l.finished_product_id is not null";
			case "unmatched" -> " and l.finished_product_id is null and coalesce(l.match_source, '') <> 'ignored'";
			case "ignored" -> " and l.match_source = 'ignored'";
			default -> "";
		};
		return jdbc.sql(SELECT + " where l.connection_id = :connection" + filter + " order by l.external_name, l.id")
			.param("connection", connectionId).query(LinkLine.class).list().stream().map(LinkLine::link).toList();
	}

	/** Bu baglantida hicbir urune bagli olmayan aktif urunlerimiz (sitede karsiligi yok ya da eslesmedi). */
	@Transactional(readOnly = true)
	List<Product> unlinkedProducts(long connectionId) {
		connections.connection(connectionId);
		return jdbc.sql("""
			select p.id, p.name, p.barcode, p.sku, p.total_stock from finished_products p
			where p.is_active and not exists (select 1 from integration_product_links l
				where l.connection_id = :connection and l.finished_product_id = p.id)
			order by p.name, p.id
			""").param("connection", connectionId).query(Product.class).list();
	}

	/** Elle bagla; urun verilmezse satir "eslestirilmesin" olur ve kendiliginden eslestirmeye girmez. */
	@Transactional
	Link link(long linkId, LinkRequest request) {
		LinkLine current = line(linkId);
		Long productId = request == null ? null : request.finishedProductId();
		if (productId != null) {
			boolean exists = jdbc.sql("select count(*) from finished_products where id = :id").param("id", productId)
				.query(Long.class).single() > 0;
			if (!exists) {
				ValidationErrors errors = new ValidationErrors();
				errors.add("finished_product_id", MessageKeys.EXISTS);
				errors.throwIfAny();
			}
		}
		jdbc.sql("""
			update integration_product_links set finished_product_id = :product, match_source = :source,
				updated_at = :now where id = :id
			""").param("product", productId).param("source", productId == null ? "ignored" : "manual")
			.param("now", Timestamps.now()).param("id", current.id()).update();
		return line(linkId).link();
	}

	/** "Eslestirilmesin" isaretini kaldirir; satir bir sonraki yenilemede yeniden kendiliginden eslesebilir. */
	@Transactional
	Link reset(long linkId) {
		LinkLine current = line(linkId);
		if (current.finishedProductId() != null && !"manual".equals(current.matchSource())
			&& !"ignored".equals(current.matchSource())) {
			throw new BusinessRuleException("Kendiliğinden eşleşen satır sıfırlanmaz; başka ürüne bağlayabilirsiniz.");
		}
		jdbc.sql("""
			update integration_product_links set finished_product_id = null, match_source = null, updated_at = :now
			where id = :id
			""").param("now", Timestamps.now()).param("id", linkId).update();
		return line(linkId).link();
	}

	private LinkLine line(long id) {
		return jdbc.sql(SELECT + " where l.id = :id").param("id", id).query(LinkLine.class).optional()
			.orElseThrow(() -> new ModelNotFoundException("IntegrationProductLink", id));
	}

	private static String cut(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}
