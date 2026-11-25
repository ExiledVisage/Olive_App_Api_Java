package com.olivamore.api.quality;

import com.olivamore.api.shared.time.Timestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * BRC/IFS hazirlik paneli (docs/QUALITY.md 4): dokumanlar, izlenebilirlik tatbikati (BRCGS 3.9: yilda en az bir,
 * alti ayda bir onerilir), acik geri cagirmalar, laboratuvar ve dolum kontrolu kapsami, bitmis urun kutle denkligi.
 * Her baslik ok / warn / bad durumuyla maddeler halinde de verilir.
 */
@Service
class Readiness {

	static final int LAB_DAYS = 90;

	static final int FILLING_DAYS = 30;

	record Item(String key, String label, String state, String detail) {
	}

	record Documents(int active, int overdue, int soon, int draft) {
	}

	/** @param state none, required (180 gun), plan (90 gun), ok */
	record Drill(String lastCode, LocalDateTime lastAt, String lastResult, Long daysSince, String state,
		int openDrills) {
	}

	record Laboratory(int days, int batches, int classified, int extraVirgin, int virgin, int lampante) {
	}

	record Filling(int days, int completedRuns, int checkedRuns, int checks, int failedChecks) {
	}

	/** @param unplaced dolup rafa konmayan; difference rafa giren - cikan - stok (0 olmali) */
	record Balance(long finishedProductId, String name, long filled, long placed, long shipped, long otherOut,
		long stock, long unplaced, long difference) {
	}

	record Panel(List<Item> items, Documents documents, Drill drill, int openRecalls, Laboratory laboratory,
		Filling filling, List<Balance> massBalance) {
	}

	private record DrillRow(String code, LocalDateTime completedAt, LocalDateTime startedAt, Integer targetMinutes) {
	}

	private final JdbcClient jdbc;
	private final QualityDocuments documents;

	Readiness(JdbcClient jdbc, QualityDocuments documents) {
		this.jdbc = jdbc;
		this.documents = documents;
	}

	@Transactional(readOnly = true)
	Panel panel() {
		LocalDateTime now = Timestamps.now();
		List<QualityDocuments.Document> docs = documents.all(null, null);
		Documents docSummary = new Documents(
			(int) docs.stream().filter(d -> "active".equals(d.status())).count(),
			(int) docs.stream().filter(d -> "overdue".equals(d.reviewState())).count(),
			(int) docs.stream().filter(d -> "soon".equals(d.reviewState())).count(),
			(int) docs.stream().filter(d -> "draft".equals(d.status())).count());

		Drill drill = drill(now);
		int openRecalls = jdbc.sql("select count(*) from recalls where kind = 'recall' and status = 'open'")
			.query(Long.class).single().intValue();
		Laboratory lab = laboratory(now.minusDays(LAB_DAYS));
		Filling filling = filling(now.minusDays(FILLING_DAYS));
		List<Balance> balance = massBalance();

		List<Item> items = new ArrayList<>();
		items.add(new Item("documents", "Dokümanlar", docSummary.active() == 0 ? "bad"
			: docSummary.overdue() > 0 ? "warn" : "ok", docSummary.active() == 0 ? "Yürürlükte doküman yok."
			: docSummary.overdue() > 0 ? docSummary.overdue() + " dokümanın gözden geçirmesi gecikti."
			: docSummary.active() + " doküman güncel" + (docSummary.soon() > 0 ? ", " + docSummary.soon()
			+ " tanesi 30 gün içinde." : ".")));
		items.add(new Item("drill", "İzlenebilirlik tatbikatı", switch (drill.state()) {
			case "ok" -> "ok";
			case "plan" -> "warn";
			default -> "bad";
		}, switch (drill.state()) {
			case "none" -> "Henüz tatbikat yapılmadı (BRCGS yılda en az bir ister, altı ayda bir önerilir).";
			case "required" -> "Son tatbikatın üzerinden " + drill.daysSince() + " gün geçti; yeni tatbikat gerekli.";
			case "plan" -> "Son tatbikat " + drill.daysSince() + " gün önce; yakında planlayın.";
			default -> "Son tatbikat " + drill.daysSince() + " gün önce ("
				+ ("passed".equals(drill.lastResult()) ? "geçti" : "kaldı") + ").";
		}));
		items.add(new Item("recalls", "Açık geri çağırma", openRecalls > 0 ? "warn" : "ok",
			openRecalls > 0 ? openRecalls + " geri çağırma açık." : "Açık geri çağırma yok."));
		int unclassified = lab.batches() - lab.classified();
		items.add(new Item("laboratory", "Laboratuvar analizi", lab.batches() == 0 ? "ok"
			: unclassified * 10 > lab.batches() ? "warn" : "ok", lab.batches() == 0
			? "Son " + LAB_DAYS + " günde zeytinyağı partisi yok."
			: lab.classified() + " / " + lab.batches() + " partinin sınıfı belli (son " + LAB_DAYS + " gün)."));
		int unchecked = filling.completedRuns() - filling.checkedRuns();
		items.add(new Item("filling", "Dolum kontrolü", filling.completedRuns() == 0 ? "ok"
			: unchecked > 0 || filling.failedChecks() > 0 ? "warn" : "ok", filling.completedRuns() == 0
			? "Son " + FILLING_DAYS + " günde tamamlanan dolum yok."
			: filling.checkedRuns() + " / " + filling.completedRuns() + " dolum kontrollü; " + filling.failedChecks()
			+ " uygunsuz kontrol (son " + FILLING_DAYS + " gün)."));
		long off = balance.stream().filter(b -> b.difference() != 0).count();
		items.add(new Item("mass_balance", "Kütle denkliği", off > 0 ? "bad" : "ok", off > 0
			? off + " üründe raf hareketleri stokla tutmuyor." : "Bitmiş ürün hareketleri stokla tutuyor."));
		return new Panel(items, docSummary, drill, openRecalls, lab, filling, balance);
	}

	private Drill drill(LocalDateTime now) {
		DrillRow last = jdbc.sql("""
			select code, completed_at, started_at, target_minutes from recalls
			where kind = 'drill' and status = 'completed' order by completed_at desc, id desc limit 1
			""").query(DrillRow.class).optional().orElse(null);
		int open = jdbc.sql("select count(*) from recalls where kind = 'drill' and status = 'open'").query(Long.class)
			.single().intValue();
		if (last == null) {
			return new Drill(null, null, null, null, "none", open);
		}
		long days = ChronoUnit.DAYS.between(last.completedAt().toLocalDate(), now.toLocalDate());
		long minutes = ChronoUnit.MINUTES.between(last.startedAt(), last.completedAt());
		String result = last.targetMinutes() == null || minutes <= last.targetMinutes() ? "passed" : "failed";
		String state = days > 180 ? "required" : days > 90 ? "plan" : "passed".equals(result) ? "ok" : "required";
		return new Drill(last.code(), last.completedAt(), result, days, state, open);
	}

	/** Donemde gelen partilerin zeytinyagi materyalleri ve sinifi (partinin en son zeytinyagi materyali). */
	private Laboratory laboratory(LocalDateTime from) {
		List<String> grades = jdbc.sql("""
			with oil as (
				select distinct on (m.production_batch_id) m.production_batch_id, m.id
				from materials m
				join production_product_types t on t.id = m.production_product_type_id
				join production_products p on p.id = t.production_product_id
				join production_batches b on b.id = m.production_batch_id
				left join production_batches pb on pb.id = b.parent_batch_id
				where p.key = 'olive_oil' and m.origin_type <> 'TANK'
					and coalesce(b.received_at, pb.received_at) >= :from
				order by m.production_batch_id, m.id desc)
			select (select v.value_text from material_attribute_values v
					join production_product_attribute_definitions d
						on d.id = v.production_product_attribute_definition_id
					join attribute_definitions a on a.id = d.attribute_definition_id
					where v.material_id = oil.id and a.attribute_key = 'oil_class' limit 1) as grade
			from oil
			""").param("from", from).query((rs, n) -> {
				String grade = rs.getString(1);
				return grade == null ? "" : grade;
			}).list();
		return new Laboratory(LAB_DAYS, grades.size(), (int) grades.stream().filter(g -> !g.isBlank()).count(),
			(int) grades.stream().filter("extra_virgin"::equals).count(),
			(int) grades.stream().filter("virgin"::equals).count(),
			(int) grades.stream().filter("lampante"::equals).count());
	}

	private Filling filling(LocalDateTime from) {
		return jdbc.sql("""
			select count(*) as completed_runs,
				count(*) filter (where exists (select 1 from filling_checks c where c.finished_product_run_id = r.id))
					as checked_runs,
				(select count(*) from filling_checks c
					join finished_product_runs rr on rr.id = c.finished_product_run_id
					where rr.status = 'completed' and rr.completed_at >= :from) as checks,
				(select count(*) from filling_checks c
					join finished_product_runs rr on rr.id = c.finished_product_run_id
					where rr.status = 'completed' and rr.completed_at >= :from
						and (not c.weight_ok or c.glass_broken or c.seal_ok is false or c.cap_ok is false
							or c.label_ok is false or c.coding_ok is false)) as failed_checks
			from finished_product_runs r where r.status = 'completed' and r.completed_at >= :from
			""").param("from", from).query((rs, n) -> new Filling(FILLING_DAYS, rs.getInt("completed_runs"),
				rs.getInt("checked_runs"), rs.getInt("checks"), rs.getInt("failed_checks"))).single();
	}

	/** Hareketi ya da dolumu olan urunler. */
	private List<Balance> massBalance() {
		return jdbc.sql("""
			select p.id as finished_product_id, p.name,
				coalesce((select sum(r.quantity) from finished_product_runs r
					where r.finished_product_id = p.id and r.status = 'completed'), 0) as filled,
				coalesce(sum(m.quantity) filter (where m.movement_type = 'stock_in'), 0) as placed,
				coalesce(sum(m.quantity) filter (where m.movement_type = 'order_pick'), 0)
					- coalesce(sum(m.quantity) filter (where m.movement_type = 'order_return'), 0) as shipped,
				coalesce(sum(m.quantity) filter (where m.movement_type = 'stock_out'), 0) as other_out,
				coalesce((select sum(s.quantity) from finished_product_storage_slot_stocks s
					where s.finished_product_id = p.id), 0) as stock
			from finished_products p
			left join finished_product_stock_movements m on m.finished_product_id = p.id
			group by p.id, p.name
			order by p.name
			""").query((rs, n) -> {
				long filled = rs.getLong("filled");
				long placed = rs.getLong("placed");
				long shipped = rs.getLong("shipped");
				long otherOut = rs.getLong("other_out");
				long stock = rs.getLong("stock");
				return new Balance(rs.getLong("finished_product_id"), rs.getString("name"), filled, placed, shipped,
					otherOut, stock, filled - placed, placed - shipped - otherOut - stock);
			}).list().stream().filter(b -> b.filled() > 0 || b.placed() > 0 || b.stock() > 0).toList();
	}

}
