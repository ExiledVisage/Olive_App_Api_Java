package com.olivamore.api.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Natürel zeytinyagi sinifi (docs/QUALITY.md 1): Turk Gida Kodeksi Zeytinyagi ve Pirina Yagi Tebligi 2017/26, Ek-1
 * kalite kriterleri ve Ek-2 duyusal kriterler (RG 8/4/2023-32157 ile degisik). En kotu deger belirler; girilmeyen
 * deger sinifi dusurmez, eksik listesine girer; asitlik yoksa sinif yok.
 */
public final class OliveOilGrade {

	public static final String CLASS_KEY = "oil_class";

	public static final String ACIDITY = "acidity";

	public enum Grade {

		EXTRA_VIRGIN("extra_virgin", "Natürel sızma"),
		VIRGIN("virgin", "Natürel birinci"),
		LAMPANTE("lampante", "Ham (rafinajlık)");

		private final String key;
		private final String label;

		Grade(String key, String label) {
			this.key = key;
			this.label = label;
		}

		public String key() {
			return key;
		}

		public String label() {
			return label;
		}

	}

	/** Sinifi dusuren deger: hangi sinifin siniri. */
	public record Failure(String key, String label, BigDecimal value, String limit, Grade blocks) {
	}

	public record Missing(String key, String label) {
	}

	/** @param grade asitlik yoksa null */
	public record Result(Grade grade, List<Failure> failures, List<Missing> missing) {
	}

	private record Rule(String key, String label, String limit, Predicate<BigDecimal> passes) {
	}

	/** Olculen degerlerin sirasi ve etiketleri. */
	public static final Map<String, String> LABELS = labels();

	private static final List<Rule> EXTRA_VIRGIN = List.of(
		max(ACIDITY, "0,8"), max("peroxide", "20"), max("k232", "2,50"), max("k270", "0,22"), max("delta_k", "0,01"),
		max("faee", "35"), new Rule("defect_median", label("defect_median"), "= 0", v -> v.signum() == 0),
		new Rule("fruity_median", label("fruity_median"), "> 0", v -> v.signum() > 0));

	private static final List<Rule> VIRGIN = List.of(
		max(ACIDITY, "2,0"), max("peroxide", "20"), max("k232", "2,60"), max("k270", "0,25"), max("delta_k", "0,01"),
		max("defect_median", "3,5"), new Rule("fruity_median", label("fruity_median"), "> 0", v -> v.signum() > 0));

	private OliveOilGrade() {
	}

	public static Result of(Map<String, BigDecimal> values) {
		List<Missing> missing = LABELS.entrySet().stream().filter(e -> values.get(e.getKey()) == null)
			.map(e -> new Missing(e.getKey(), e.getValue())).toList();
		if (values.get(ACIDITY) == null) {
			return new Result(null, List.of(), missing);
		}
		List<Failure> extraVirgin = failures(EXTRA_VIRGIN, values, Grade.EXTRA_VIRGIN);
		if (extraVirgin.isEmpty()) {
			return new Result(Grade.EXTRA_VIRGIN, List.of(), missing);
		}
		List<Failure> virgin = failures(VIRGIN, values, Grade.VIRGIN);
		if (virgin.isEmpty()) {
			return new Result(Grade.VIRGIN, extraVirgin, missing);
		}
		return new Result(Grade.LAMPANTE, virgin, missing);
	}

	public static Grade grade(String key) {
		for (Grade grade : Grade.values()) {
			if (grade.key.equals(key)) {
				return grade;
			}
		}
		return null;
	}

	private static List<Failure> failures(List<Rule> rules, Map<String, BigDecimal> values, Grade grade) {
		List<Failure> out = new ArrayList<>();
		for (Rule rule : rules) {
			BigDecimal value = values.get(rule.key());
			if (value != null && !rule.passes().test(value)) {
				out.add(new Failure(rule.key(), rule.label(), value, rule.limit(), grade));
			}
		}
		return out;
	}

	private static Rule max(String key, String limit) {
		BigDecimal bound = new BigDecimal(limit.replace(',', '.'));
		return new Rule(key, label(key), "≤ " + limit, v -> v.compareTo(bound) <= 0);
	}

	private static String label(String key) {
		return LABELS.get(key);
	}

	private static Map<String, String> labels() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put(ACIDITY, "Asitlik");
		labels.put("peroxide", "Peroksit");
		labels.put("k232", "K232");
		labels.put("k270", "K270");
		labels.put("delta_k", "Delta K");
		labels.put("faee", "Yağ asidi etil esterleri");
		labels.put("defect_median", "Kusur medyanı (Md)");
		labels.put("fruity_median", "Meyvemsilik medyanı (Mf)");
		return Collections.unmodifiableMap(labels);
	}

}
