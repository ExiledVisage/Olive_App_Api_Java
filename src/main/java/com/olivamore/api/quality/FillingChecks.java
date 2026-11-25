package com.olivamore.api.quality;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Dolum kontrolu (docs/QUALITY.md 2): kosunun hat hazirligi ve saatlik kontrolleri. Hedef agirlik urunun hacmi x
 * zeytinyagi yogunlugu; tolerans varsayilani hedefin %1,5'i (en az 1 g). Uygunluk sunucuda: |ortalama - hedef| <=
 * tolerans. Kontrol hat hazirligindan sonra, devam eden ya da tamamlanmis kosuda girilir.
 */
@Service
class FillingChecks {

	static final BigDecimal DEFAULT_DENSITY = new BigDecimal("0.916");

	record User(Long id, String name) {
	}

	record Setup(boolean conveyorClean, boolean airChecked, boolean previousProductCleared, String note,
		LocalDateTime approvedAt, User approvedBy) {
	}

	record Check(long id, LocalDateTime checkedAt, List<BigDecimal> weights, BigDecimal averageG, BigDecimal targetG,
		BigDecimal toleranceG, boolean weightOk, Boolean sealOk, Boolean capOk, Boolean labelOk, Boolean codingOk,
		boolean glassBroken, String note, User checkedBy, boolean ok) {
	}

	/** @param targetG urunun varsayilan hedefi (hacim yoksa null) */
	record Summary(int checkCount, int failedCount, BigDecimal targetG, BigDecimal toleranceG) {
	}

	record RunChecks(long runId, String runStatus, Setup setup, List<Check> checks, Summary summary) {
	}

	record SetupRequest(Boolean conveyorClean, Boolean airChecked, Boolean previousProductCleared, String note) {
	}

	record CheckRequest(LocalDateTime checkedAt, List<BigDecimal> weights, BigDecimal targetG, BigDecimal toleranceG,
		Boolean sealOk, Boolean capOk, Boolean labelOk, Boolean codingOk, Boolean glassBroken, String note) {
	}

	private record Run(long id, String status, BigDecimal volumeLitre) {
	}

	private record CheckRow(long id, LocalDateTime checkedAt, BigDecimal weight1, BigDecimal weight2,
		BigDecimal weight3, BigDecimal weight4, BigDecimal weight5, BigDecimal averageG, BigDecimal targetG,
		BigDecimal toleranceG, boolean weightOk, Boolean sealOk, Boolean capOk, Boolean labelOk, Boolean codingOk,
		boolean glassBroken, String note, Long userId, String userName) {

		Check check() {
			List<BigDecimal> weights = Stream.of(weight1, weight2, weight3, weight4, weight5).filter(Objects::nonNull)
				.toList();
			boolean ok = weightOk && !glassBroken && Stream.of(sealOk, capOk, labelOk, codingOk)
				.noneMatch(Boolean.FALSE::equals);
			return new Check(id, checkedAt, weights, averageG, targetG, toleranceG, weightOk, sealOk, capOk, labelOk,
				codingOk, glassBroken, note, userId == null ? null : new User(userId, userName), ok);
		}

	}

	private record SetupRow(boolean conveyorClean, boolean airChecked, boolean previousProductCleared, String note,
		LocalDateTime approvedAt, Long userId, String userName) {
	}

	private final JdbcClient jdbc;

	FillingChecks(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	RunChecks of(long runId) {
		Run run = run(runId);
		Setup setup = jdbc.sql("""
			select s.conveyor_clean, s.air_checked, s.previous_product_cleared, s.note, s.approved_at,
				s.approved_by_user_id as user_id, u.name as user_name
			from filling_setups s left join users u on u.id = s.approved_by_user_id
			where s.finished_product_run_id = :run
			""").param("run", runId).query(SetupRow.class).optional()
			.map(r -> new Setup(r.conveyorClean(), r.airChecked(), r.previousProductCleared(), r.note(),
				r.approvedAt(), r.userId() == null ? null : new User(r.userId(), r.userName())))
			.orElse(null);
		List<Check> checks = jdbc.sql("""
			select c.id, c.checked_at, c.weight_1 as weight1, c.weight_2 as weight2, c.weight_3 as weight3,
				c.weight_4 as weight4, c.weight_5 as weight5, c.average_g,
				c.target_g, c.tolerance_g, c.weight_ok, c.seal_ok, c.cap_ok, c.label_ok, c.coding_ok, c.glass_broken,
				c.note, c.checked_by_user_id as user_id, u.name as user_name
			from filling_checks c left join users u on u.id = c.checked_by_user_id
			where c.finished_product_run_id = :run order by c.checked_at, c.id
			""").param("run", runId).query(CheckRow.class).list().stream().map(CheckRow::check).toList();
		BigDecimal target = defaultTarget(run);
		Summary summary = new Summary(checks.size(), (int) checks.stream().filter(c -> !c.ok()).count(), target,
			target == null ? null : defaultTolerance(target));
		return new RunChecks(runId, run.status(), setup, checks, summary);
	}

	/** Uc onay da verilmeli; tekrar verilirse onaylayan ve zaman yenilenir. */
	@Transactional
	RunChecks setup(long runId, SetupRequest request, long userId) {
		Run run = run(runId);
		requireActive(run);
		ValidationErrors errors = new ValidationErrors();
		if (!Boolean.TRUE.equals(request.conveyorClean())) {
			errors.add("conveyor_clean", "Bant temizliği onaylanmalıdır.");
		}
		if (!Boolean.TRUE.equals(request.airChecked())) {
			errors.add("air_checked", "Basınçlı hava kontrolü onaylanmalıdır.");
		}
		if (!Boolean.TRUE.equals(request.previousProductCleared())) {
			errors.add("previous_product_cleared", "Önceki ürünün hattan kaldırıldığı onaylanmalıdır.");
		}
		errors.throwIfAny();
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			insert into filling_setups (finished_product_run_id, conveyor_clean, air_checked, previous_product_cleared,
				note, approved_by_user_id, approved_at, created_at, updated_at)
			values (:run, true, true, true, :note, :user, :now, :now, :now)
			on conflict (finished_product_run_id) do update set note = excluded.note,
				approved_by_user_id = excluded.approved_by_user_id, approved_at = excluded.approved_at,
				updated_at = excluded.updated_at
			""").param("run", runId).param("note", blankToNull(request.note())).param("user", userId)
			.param("now", now).update();
		return of(runId);
	}

	@Transactional
	RunChecks record(long runId, CheckRequest request, long userId) {
		Run run = run(runId);
		requireActive(run);
		boolean ready = jdbc.sql("select count(*) from filling_setups where finished_product_run_id = :run")
			.param("run", runId).query(Long.class).single() > 0;
		if (!ready) {
			throw new BusinessRuleException("Saatlik kontrolden önce hat hazırlığı onaylanmalıdır.");
		}
		ValidationErrors errors = new ValidationErrors();
		List<BigDecimal> weights = request.weights() == null ? List.of() : request.weights().stream()
			.filter(Objects::nonNull).toList();
		if (weights.isEmpty() || weights.size() > 5) {
			errors.add("weights", "1 ile 5 arasında numune ağırlığı girilmelidir.");
		}
		if (weights.stream().anyMatch(w -> w.signum() <= 0)) {
			errors.add("weights", "Numune ağırlıkları sıfırdan büyük olmalıdır.");
		}
		BigDecimal target = request.targetG() != null ? request.targetG() : defaultTarget(run);
		if (target == null) {
			errors.add("target_g", MessageKeys.REQUIRED);
		}
		else if (target.signum() <= 0) {
			errors.add("target_g", "Hedef ağırlık sıfırdan büyük olmalıdır.");
		}
		BigDecimal tolerance = request.toleranceG() != null ? request.toleranceG()
			: target == null ? null : defaultTolerance(target);
		if (tolerance != null && tolerance.signum() < 0) {
			errors.add("tolerance_g", "Tolerans negatif olamaz.");
		}
		boolean glass = Boolean.TRUE.equals(request.glassBroken());
		if (glass && blankToNull(request.note()) == null) {
			errors.add("note", "Cam kırığı varsa not zorunludur.");
		}
		LocalDateTime checkedAt = request.checkedAt() != null ? request.checkedAt() : Timestamps.now();
		if (checkedAt.isAfter(Timestamps.now().plusMinutes(5))) {
			errors.add("checked_at", "Kontrol zamanı ileri bir tarih olamaz.");
		}
		errors.throwIfAny();

		BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal average = sum.divide(BigDecimal.valueOf(weights.size()), 2, RoundingMode.HALF_UP);
		boolean weightOk = average.subtract(target).abs().compareTo(tolerance) <= 0;
		List<BigDecimal> padded = new ArrayList<>(weights);
		while (padded.size() < 5) {
			padded.add(null);
		}
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			insert into filling_checks (finished_product_run_id, checked_at, weight_1, weight_2, weight_3, weight_4,
				weight_5, target_g, tolerance_g, average_g, weight_ok, seal_ok, cap_ok, label_ok, coding_ok,
				glass_broken,
				note, checked_by_user_id, created_at, updated_at)
			values (:run, :at, :w1, :w2, :w3, :w4, :w5, :target, :tolerance, :average, :weightOk, :seal, :cap, :label,
				:coding, :glass, :note, :user, :now, :now)
			""").param("run", runId).param("at", checkedAt).param("w1", padded.get(0)).param("w2", padded.get(1))
			.param("w3", padded.get(2)).param("w4", padded.get(3)).param("w5", padded.get(4)).param("target", target)
			.param("tolerance", tolerance).param("average", average).param("weightOk", weightOk)
			.param("seal", request.sealOk()).param("cap", request.capOk()).param("label", request.labelOk())
			.param("coding", request.codingOk()).param("glass", glass).param("note", blankToNull(request.note()))
			.param("user", userId).param("now", now).update();
		return of(runId);
	}

	@Transactional
	RunChecks delete(long checkId) {
		Long runId = jdbc.sql("select finished_product_run_id from filling_checks where id = :id").param("id", checkId)
			.query(Long.class).optional().orElseThrow(() -> new ModelNotFoundException("FillingCheck", checkId));
		jdbc.sql("delete from filling_checks where id = :id").param("id", checkId).update();
		return of(runId);
	}

	private Run run(long runId) {
		return jdbc.sql("""
			select r.id, r.status, p.volume_litre from finished_product_runs r
			join finished_products p on p.id = r.finished_product_id where r.id = :id
			""").param("id", runId).query(Run.class).optional()
			.orElseThrow(() -> new ModelNotFoundException("FinishedProductRun", runId));
	}

	private static void requireActive(Run run) {
		if ("planned".equals(run.status()) || "cancelled".equals(run.status())) {
			throw new BusinessRuleException("Dolum kontrolü yalnızca başlamış ya da tamamlanmış dolumda girilir.");
		}
	}

	/** Hacim x zeytinyagi yogunlugu, gram, tam sayi. */
	private BigDecimal defaultTarget(Run run) {
		if (run.volumeLitre() == null || run.volumeLitre().signum() <= 0) {
			return null;
		}
		BigDecimal density = jdbc.sql("select coalesce(max(default_density_kg_per_litre), :fallback)"
			+ " from production_products where key = 'olive_oil'").param("fallback", DEFAULT_DENSITY)
			.query(BigDecimal.class).single();
		return run.volumeLitre().multiply(density).multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP);
	}

	private static BigDecimal defaultTolerance(BigDecimal target) {
		return target.multiply(new BigDecimal("0.015")).setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
