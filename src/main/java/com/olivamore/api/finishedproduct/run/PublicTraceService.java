package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.finishedproduct.RunOriginDirectory;
import com.olivamore.api.inventory.MaterialDirectory;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Sisedeki QR (docs/GREENOPS_FEATURES.md 5b): dolum kosusuna tahmin edilemez bir anahtar; adres
 * {@code <public-base-url>/api/public/runs/<anahtar>}. Sayfa giris istemez, yalnizca secili bilgileri gosterir:
 * urun, lot (dolum), dolum tarihi, asitlik, cesit, hasat donemi (teslimatlarin varis araligi), tedarikci ve tarla.
 * Musteri, fiyat, kullanici ve tank gosterilmez. Alan adi gelene kadar sayfa "deneme" uyarisi tasir.
 */
@Service
class PublicTraceService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final FinishedProductRunRepository runs;
	private final RunQueries queries;
	private final MaterialDirectory materials;
	private final RunOriginDirectory origins;
	private final String baseUrl;

	PublicTraceService(FinishedProductRunRepository runs, RunQueries queries, MaterialDirectory materials,
		RunOriginDirectory origins, @Value("${olive.public-base-url}") String baseUrl) {
		this.runs = runs;
		this.queries = queries;
		this.materials = materials;
		this.origins = origins;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
	}

	/** Bag yoksa acilir; {@code regenerate} ise eski QR gecersiz olur. Yalnizca baslamis ya da bitmis kosu. */
	@Transactional
	String link(long runId, boolean regenerate) {
		FinishedProductRun run = find(runId);
		if (run.getStatus() != RunStatus.IN_PROGRESS && run.getStatus() != RunStatus.COMPLETED) {
			throw new BusinessRuleException("Şişe QR'ı yalnızca başlamış ya da tamamlanmış dolum için oluşturulur.");
		}
		if (run.getPublicToken() == null || regenerate) {
			run.changePublicToken(token());
		}
		return url(run.getPublicToken());
	}

	/** Bag kaldirilir; basilmis QR'lar artik sayfa acmaz. */
	@Transactional
	void revoke(long runId) {
		find(runId).changePublicToken(null);
	}

	String url(String token) {
		return token == null ? null : baseUrl + "/api/public/runs/" + token;
	}

	/** Herkese acik sayfa; anahtar yoksa bos (404). */
	@Transactional(readOnly = true)
	Optional<String> page(String token) {
		if (token == null || token.isBlank() || token.length() > 40) {
			return Optional.empty();
		}
		return runs.findByPublicToken(token).map(this::render);
	}

	private String render(FinishedProductRun run) {
		List<Long> sources = queries.materialDisplays(List.of(run.getId())).stream()
			.map(RunQueries.MaterialDisplay::sourceMaterialId).toList();
		List<RunOriginDirectory.Delivery> deliveries = origins.deliveries(
			materials.originBatchIds(run.getId(), sources));
		Set<String> cultivars = new LinkedHashSet<>();
		deliveries.forEach(d -> cultivars.addAll(d.cultivars()));
		List<LocalDate> days = deliveries.stream().map(RunOriginDirectory.Delivery::receivedAt)
			.filter(Objects::nonNull).map(LocalDateTime::toLocalDate).sorted().toList();
		LocalDateTime filled = run.getCompletedAt() != null ? run.getCompletedAt() : run.getStartedAt();

		StringBuilder rows = new StringBuilder();
		row(rows, "Ürün", run.getProduct().getName());
		row(rows, "Lot", "Dolum #" + run.getId());
		row(rows, "Dolum tarihi", filled == null ? null : DAY.format(filled));
		for (RunQueries.ValueDisplay value : queries.valueDisplays(List.of(run.getId()))) {
			BigDecimal number = value.actualValue() != null ? value.actualValue() : value.estimatedValue();
			row(rows, value.attributeLabel(), number == null ? null : number.stripTrailingZeros().toPlainString());
		}
		row(rows, "Çeşit", cultivars.isEmpty() ? null : String.join(", ", cultivars));
		row(rows, "Hasat dönemi", days.isEmpty() ? null : DAY.format(days.getFirst())
			+ (days.getFirst().equals(days.getLast()) ? "" : " – " + DAY.format(days.getLast())));

		StringBuilder origin = new StringBuilder();
		deliveries.stream().map(d -> String.join(" · ", List.of(d.supplierName(), d.fieldCode(), d.fieldLocation())
				.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).toList()))
			.filter(s -> !s.isBlank()).distinct().sorted(Comparator.naturalOrder())
			.forEach(s -> origin.append("<li>").append(escape(s)).append("</li>"));

		return """
			<!doctype html>
			<html lang="tr"><head><meta charset="utf-8">
			<meta name="viewport" content="width=device-width, initial-scale=1">
			<title>%s - Olivamore</title>
			<style>
			body{font-family:system-ui,sans-serif;margin:0;background:#f6f5ef;color:#2b2b2b}
			main{max-width:560px;margin:0 auto;padding:20px}
			.demo{background:#fff3cd;border:1px solid #e0c36b;border-radius:8px;padding:10px 12px;font-size:14px}
			h1{font-size:22px;color:#556b2f;margin:18px 0 6px}
			table{width:100%%;border-collapse:collapse;background:#fff;border-radius:8px}
			td{padding:10px 12px;border-bottom:1px solid #eee;vertical-align:top}
			td:first-child{color:#777;width:40%%}
			ul{background:#fff;border-radius:8px;padding:10px 12px 10px 30px}
			footer{color:#999;font-size:12px;margin-top:24px}
			</style></head><body><main>
			<div class="demo">Bu sayfa deneme amaçlıdır; bilgiler geliştirme sunucusundaki örnek verilerdir.</div>
			<h1>%s</h1>
			<table>%s</table>
			<h1>Zeytinin geldiği yer</h1>
			%s
			<footer>Olivamore izlenebilirlik</footer>
			</main></body></html>
			""".formatted(escape(run.getProduct().getName()), escape(run.getProduct().getName()), rows,
			origin.isEmpty() ? "<p>Bilgi yok.</p>" : "<ul>" + origin + "</ul>");
	}

	private static void row(StringBuilder rows, String label, String value) {
		if (value != null && !value.isBlank()) {
			rows.append("<tr><td>").append(escape(label)).append("</td><td>").append(escape(value))
				.append("</td></tr>");
		}
	}

	static String escape(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	private static String token() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private FinishedProductRun find(long id) {
		return runs.findById(id).orElseThrow(() -> new ModelNotFoundException("FinishedProductRun", id));
	}

}
