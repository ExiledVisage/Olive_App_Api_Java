package com.olivamore.api.shared.validation;

import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.web.PhpNumbers;
import com.olivamore.api.shared.web.RequestInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Laravel {@code Illuminate\Validation\Validator}'un Java karsiligi. FormRequest'lerdeki
 * kurallar ayni metinle yazilir:
 * <pre>
 * ValidatedInput v = validator.validate(input, Rules.of()
 *     .field("email", "required|string|email|max:255|unique:users,email")
 *     .field("roles", "nullable|array")
 *     .field("roles.*", "string|exists:roles,name"));
 * </pre>
 * Laravel'den birebir alinan davranislar:
 * <ul>
 *   <li>Kural yalnizca alan istekte varsa calisir; implicit kurallar ({@code required}...) her zaman</li>
 *   <li>{@code nullable} alan null ise diger kurallar atlanir; {@code sometimes} alan yoksa hepsi atlanir</li>
 *   <li>Bir implicit kural duserse o alanin geri kalan kurallari calismaz; {@code bail} ilk hatada durdurur</li>
 *   <li>{@code unique} / {@code exists} o alanda onceki bir hata varsa calismaz</li>
 *   <li>Alanda birden fazla kural duserse hepsinin mesaji, kural sirasiyla</li>
 *   <li>{@code roles.*} istekteki elemanlara genisler ({@code roles.0}, {@code roles.1}...)</li>
 *   <li>{@code min}/{@code max} mesaj tipi alanin kurallarina gore: numeric/integer varsa sayi,
 *       array varsa eleman sayisi, yoksa karakter sayisi</li>
 * </ul>
 * Yeni kural gerektiginde Laravel'in {@code Concerns\ValidatesAttributes} kodundan birebir eklenir.
 * Bilinmeyen kural ilk kullanimda hata verir (sessizce gecmez).
 */
@Component
public class LaravelValidator {

	private static final Set<String> IMPLICIT = Set.of("accepted", "accepted_if", "declined", "declined_if", "filled",
		"missing", "missing_if", "missing_unless", "missing_with", "missing_with_all", "present", "present_if",
		"present_unless", "present_with", "present_with_all", "required", "required_if", "required_if_accepted",
		"required_if_declined", "required_unless", "required_with", "required_with_all", "required_without",
		"required_without_all");

	private static final Set<String> NUMERIC_RULES = Set.of("numeric", "integer", "decimal");
	private static final Set<String> MARKERS = Set.of("nullable", "sometimes", "bail");
	private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private final JdbcTemplate jdbc;
	private final JsonMapper json;

	public LaravelValidator(JdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Hata varsa {@link LaravelValidationException} (422). */
	public ValidatedInput validate(RequestInput input, Rules rules) {
		Map<String, Object> data = input.all();
		Map<String, List<String>> errors = new LinkedHashMap<>();
		Map<String, String> patterns = new LinkedHashMap<>();
		Map<String, List<ParsedRule>> expanded = expand(rules.fields(), data, patterns);

		for (Map.Entry<String, List<ParsedRule>> entry : expanded.entrySet()) {
			String attribute = entry.getKey();
			List<ParsedRule> attributeRules = entry.getValue();
			Set<String> failed = new LinkedHashSet<>();
			for (ParsedRule rule : attributeRules) {
				if (MARKERS.contains(rule.name())) {
					continue;
				}
				Object value = RequestInput.Paths.value(data, attribute);
				if (validatable(rule, attribute, attributeRules, value, data, errors)
					&& !passes(rule, attribute, attributeRules, value, data, patterns)) {
					failed.add(rule.name());
					errors.computeIfAbsent(attribute, k -> new ArrayList<>())
						.add(message(rule, attribute, attributeRules, patterns.containsKey(attribute)));
				}
				if (stopValidating(attributeRules, failed, errors.containsKey(attribute))) {
					break;
				}
			}
		}
		if (!errors.isEmpty()) {
			throw new LaravelValidationException(errors);
		}
		return new ValidatedInput(validated(expanded, data), json);
	}

	// ---- Validator::passes() yardimcilari ----------------------------------

	private static boolean validatable(ParsedRule rule, String attribute, List<ParsedRule> rules, Object value,
		Map<String, Object> data, Map<String, List<String>> errors) {
		boolean implicit = IMPLICIT.contains(rule.name());
		// presentOrRuleIsImplicit
		boolean present = RequestInput.Paths.exists(data, attribute);
		if (value instanceof String s && s.isBlank()) {
			if (!implicit) {
				return false;
			}
		}
		else if (!present && !implicit) {
			return false;
		}
		// passesOptionalCheck
		if (has(rules, "sometimes") && !present) {
			return false;
		}
		// isNotNullIfMarkedAsNullable
		if (!implicit && has(rules, "nullable") && value == null) {
			return false;
		}
		// hasNotFailedPreviousRuleIfPresenceRule
		if ((rule.name().equals("unique") || rule.name().equals("exists")) && errors.containsKey(attribute)) {
			return false;
		}
		return true;
	}

	private static boolean stopValidating(List<ParsedRule> rules, Set<String> failed, boolean hasErrors) {
		if (has(rules, "bail")) {
			return hasErrors;
		}
		return rules.stream().anyMatch(r -> IMPLICIT.contains(r.name())) && failed.stream().anyMatch(IMPLICIT::contains);
	}

	/**
	 * {@code validated()}: kurali olan ve istekte bulunan alanlar, kural sirasiyla.
	 * {@code excludeUnvalidatedArrayKeys} (Laravel 10+ varsayilani): {@code array} kurali olan bir
	 * alanin alt alanlarina da kural varsa alan butun olarak degil, yalnizca dogrulanan alt
	 * alanlariyla alinir.
	 */
	private static Map<String, Object> validated(Map<String, List<ParsedRule>> rules, Map<String, Object> data) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<String, List<ParsedRule>> entry : rules.entrySet()) {
			String key = entry.getKey();
			if (!RequestInput.Paths.exists(data, key)) {
				continue;
			}
			Object value = RequestInput.Paths.value(data, key);
			boolean arrayRule = has(entry.getValue(), "array") || has(entry.getValue(), "list");
			if (arrayRule && value != null && rules.keySet().stream().anyMatch(k -> k.startsWith(key + "."))) {
				continue;
			}
			setPath(out, key, value);
		}
		return listify(out);
	}

	/** {@code Arr::set}: nokta yoluyla ic ice yazar. */
	@SuppressWarnings("unchecked")
	private static void setPath(Map<String, Object> root, String path, Object value) {
		String[] parts = path.split("\\.");
		Map<String, Object> current = root;
		for (int i = 0; i < parts.length - 1; i++) {
			Object next = current.get(parts[i]);
			if (!(next instanceof Map<?, ?>)) {
				next = new LinkedHashMap<String, Object>();
				current.put(parts[i], next);
			}
			current = (Map<String, Object>) next;
		}
		current.put(parts[parts.length - 1], value);
	}

	/** PHP dizisi: anahtarlari 0..n-1 olan dizi JSON'da liste olur. */
	@SuppressWarnings("unchecked")
	private static <T> T listify(T value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			((Map<String, Object>) map).forEach((k, v) -> out.put(k, listify(v)));
			boolean sequential = !out.isEmpty();
			int i = 0;
			for (String k : out.keySet()) {
				if (!k.equals(String.valueOf(i++))) {
					sequential = false;
					break;
				}
			}
			return (T) (sequential ? new ArrayList<>(out.values()) : out);
		}
		if (value instanceof List<?> list) {
			return (T) new ArrayList<>(list.stream().map(LaravelValidator::listify).toList());
		}
		return value;
	}

	// ---- kurallar (Concerns\ValidatesAttributes) ---------------------------

	private boolean passes(ParsedRule rule, String attribute, List<ParsedRule> rules, Object value,
		Map<String, Object> data, Map<String, String> patterns) {
		List<String> p = rule.parameters();
		return switch (rule.name()) {
			case "required" -> required(value);
			// validateRequiredWith: parametrelerden biri doluysa bu alan zorunlu
			case "required_with" -> p.stream().allMatch(k -> !required(RequestInput.Paths.value(data, k))) || required(value);
			// validateRequiredWithout: parametrelerden biri bossa bu alan zorunlu
			case "required_without" -> p.stream().noneMatch(k -> !required(RequestInput.Paths.value(data, k))) || required(value);
			// validateProhibits: bu alan doluysa parametrelerin hepsi bos olmali
			case "prohibits" -> !required(value) || p.stream().noneMatch(k -> required(RequestInput.Paths.value(data, k)));
			// validateDifferent: istekte olan parametrelerle === degil
			case "different" -> p.stream().noneMatch(k -> RequestInput.Paths.exists(data, k)
				&& phpIdentical(value, RequestInput.Paths.value(data, k)));
			case "distinct" -> distinct(attribute, value, patterns.getOrDefault(attribute, attribute), data);
			case "present" -> true; // validatable zaten varligi kontrol etti
			case "filled" -> !RequestInput.Paths.exists(data, attribute) || required(value);
			case "string" -> value instanceof String;
			case "integer" -> integer(value);
			case "numeric" -> numeric(value);
			case "boolean" -> value instanceof Boolean || Objects.equals(value, 0L) || Objects.equals(value, 1L)
				|| "0".equals(value) || "1".equals(value);
			case "array" -> value instanceof List<?> || value instanceof Map<?, ?>;
			case "email" -> value instanceof String s && EmailRule.isValid(s);
			case "min" -> size(value, rules) >= number(p.getFirst());
			case "max" -> size(value, rules) <= number(p.getFirst());
			case "size" -> size(value, rules) == number(p.getFirst());
			// validateGt (yalnizca sabit sayi parametresi): deger sayisal degilse kalir
			case "gt" -> numeric(value) && size(value, rules) > literal(p.getFirst());
			case "between" -> {
				double size = size(value, rules);
				yield size >= number(p.get(0)) && size <= number(p.get(1));
			}
			case "in" -> in(value, p, rules);
			case "not_in" -> !in(value, p, rules);
			// validateConfirmed -> validateSame: $value === Arr::get($data, "x_confirmation")
			case "confirmed" -> phpIdentical(value, RequestInput.Paths.value(data, attribute + "_confirmation"));
			case "same" -> phpIdentical(value, RequestInput.Paths.value(data, p.getFirst()));
			case "unique" -> unique(value, p);
			case "exists" -> exists(value, p);
			default -> throw new IllegalArgumentException("Desteklenmeyen Laravel kurali: " + rule.name()
				+ " (LaravelValidator'a Laravel'in davranisiyla eklenmeli)");
		};
	}

	/**
	 * {@code validateDistinct}: ayni desene uyan diger degerlerde (kendisi haric) PHP {@code ==} ile esit
	 * olan yoksa gecer. Tekrar eden elemanlarin her biri hata alir.
	 */
	private static boolean distinct(String attribute, Object value, String pattern, Map<String, Object> data) {
		// Desen parcalari tirnaklanir, "*" bir anahtar parcasina ([^.]+) karsilik gelir
		StringBuilder regexText = new StringBuilder("^");
		String[] parts = pattern.split("\\.", -1);
		for (int i = 0; i < parts.length; i++) {
			regexText.append(i == 0 ? "" : "[.]").append(parts[i].equals("*") ? "[^.]+" : Pattern.quote(parts[i]));
		}
		Pattern regex = Pattern.compile(regexText.append("$").toString());
		List<Object> others = new ArrayList<>();
		dot("", data, (key, v) -> {
			if (!key.equals(attribute) && regex.matcher(key).matches()) {
				others.add(v);
			}
		});
		return others.stream().noneMatch(o -> phpLooseEquals(value, o));
	}

	/** {@code Arr::dot} */
	private static void dot(String prefix, Object node, java.util.function.BiConsumer<String, Object> out) {
		if (node instanceof Map<?, ?> map && !map.isEmpty()) {
			map.forEach((k, v) -> dot(join(prefix, String.valueOf(k)), v, out));
		}
		else if (node instanceof List<?> list && !list.isEmpty()) {
			for (int i = 0; i < list.size(); i++) {
				dot(join(prefix, String.valueOf(i)), list.get(i), out);
			}
		}
		else if (!prefix.isEmpty()) {
			out.accept(prefix, node);
		}
	}

	/** PHP 8 {@code ==} (skaler degerler icin). */
	static boolean phpLooseEquals(Object a, Object b) {
		if (a == null || b == null) {
			Object other = a == null ? b : a;
			return other == null || Boolean.FALSE.equals(other) || "".equals(other) || isZero(other);
		}
		if (a instanceof Boolean || b instanceof Boolean) {
			return truthy(a) == truthy(b);
		}
		if (numeric(a) && numeric(b)) {
			return new BigDecimal(phpString(a).strip()).compareTo(new BigDecimal(phpString(b).strip())) == 0;
		}
		return phpString(a).equals(phpString(b));
	}

	private static boolean isZero(Object v) {
		return v instanceof Number n && new BigDecimal(n.toString()).signum() == 0;
	}

	private static boolean truthy(Object v) {
		return switch (v) {
			case null -> false;
			case Boolean b -> b;
			case String s -> !s.isEmpty() && !s.equals("0");
			case Collection<?> c -> !c.isEmpty();
			case Map<?, ?> m -> !m.isEmpty();
			default -> !isZero(v);
		};
	}

	/** {@code validateRequired} */
	static boolean required(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof String s) {
			return !s.trim().isEmpty();
		}
		if (value instanceof Collection<?> c) {
			return !c.isEmpty();
		}
		if (value instanceof Map<?, ?> m) {
			return !m.isEmpty();
		}
		return true;
	}

	/** {@code filter_var($value, FILTER_VALIDATE_INT) !== false} */
	static boolean integer(Object value) {
		return switch (value) {
			case Long l -> true;
			case BigInteger b -> false; // PHP int sinirini asar
			case BigDecimal d -> d.stripTrailingZeros().scale() <= 0 && d.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0;
			case Boolean b -> b; // (string) true === "1"
			case String s -> PhpNumbers.validateInt(s).isPresent();
			case null, default -> false;
		};
	}

	/** {@code is_numeric} */
	static boolean numeric(Object value) {
		return switch (value) {
			case Long l -> true;
			case BigInteger b -> true;
			case BigDecimal d -> true;
			case String s -> PhpNumbers.numeric(s).isPresent();
			case null, default -> false;
		};
	}

	/** {@code gt:N}: alan adiyla karsilastirma (gt:other_field) desteklenmiyor. */
	private static double literal(String parameter) {
		if (PhpNumbers.numeric(parameter).isEmpty()) {
			throw new IllegalArgumentException("gt kuralinda alan karsilastirmasi desteklenmiyor: " + parameter);
		}
		return number(parameter);
	}

	/** {@code getSize}: alan sayisal kurala sahipse ve deger sayiysa deger, dizi ise eleman sayisi, yoksa uzunluk. */
	private static double size(Object value, List<ParsedRule> rules) {
		boolean hasNumeric = rules.stream().anyMatch(r -> NUMERIC_RULES.contains(r.name()));
		if (hasNumeric && numeric(value)) {
			return value instanceof String s ? PhpNumbers.numeric(s).orElse(0) : ((Number) value).doubleValue();
		}
		if (value instanceof Collection<?> c) {
			return c.size();
		}
		if (value instanceof Map<?, ?> m) {
			return m.size();
		}
		String text = phpString(value);
		return text.codePointCount(0, text.length());
	}

	private static boolean in(Object value, List<String> options, List<ParsedRule> rules) {
		if (value instanceof Collection<?> c && has(rules, "array")) {
			return c.stream().allMatch(v -> options.contains(phpString(v)));
		}
		return !(value instanceof Collection<?>) && !(value instanceof Map<?, ?>) && options.contains(phpString(value));
	}

	/** {@code unique:table,column,ignoreId,idColumn} */
	private boolean unique(Object value, List<String> p) {
		String table = identifier(p.get(0));
		String column = identifier(p.size() > 1 ? p.get(1) : "id");
		StringBuilder sql = new StringBuilder("select count(*) from ").append(table).append(" where ").append(column).append(" = ?");
		List<Object> args = new ArrayList<>(List.of(value));
		if (p.size() > 2 && !p.get(2).isEmpty() && !"NULL".equalsIgnoreCase(p.get(2))) {
			sql.append(" and ").append(identifier(p.size() > 3 && !p.get(3).isEmpty() ? p.get(3) : "id")).append(" <> ?");
			args.add(p.get(2));
		}
		// Rule::unique(...)->where(kolon, deger): unique:tablo,kolon,haric,id,kolon2,deger2,...
		for (int i = 4; i + 1 < p.size(); i += 2) {
			String column2 = identifier(p.get(i));
			String expected = p.get(i + 1);
			if ("NULL".equals(expected)) {
				sql.append(" and ").append(column2).append(" is null");
			}
			else if ("NOT_NULL".equals(expected)) {
				sql.append(" and ").append(column2).append(" is not null");
			}
			else {
				sql.append(" and ").append(column2).append(" = ?");
				args.add(expected);
			}
		}
		return count(sql.toString(), args) == 0;
	}

	/** {@code exists:table,column}; dizi degerde her farkli eleman var olmali. */
	private boolean exists(Object value, List<String> p) {
		String table = identifier(p.get(0));
		String column = identifier(p.size() > 1 ? p.get(1) : "id");
		if (value instanceof Collection<?> c) {
			List<Object> distinct = new ArrayList<>(new LinkedHashSet<>(c));
			if (distinct.isEmpty()) {
				return true;
			}
			String marks = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
			return count("select count(distinct " + column + ") from " + table + " where " + column + " in (" + marks + ")",
				distinct) >= distinct.size();
		}
		return count("select count(*) from " + table + " where " + column + " = ?", List.of(value)) >= 1;
	}

	/**
	 * Degerler PDO gibi tipsiz gonderilir (Types.OTHER = PostgreSQL "unknown"): sunucu kolon
	 * tipine gore cevirir. "5" bigint kolona uyar, "abc" Laravel'deki gibi SQL hatasi (500) verir.
	 */
	private long count(String sql, List<Object> args) {
		Long n = jdbc.query(sql, ps -> {
			for (int i = 0; i < args.size(); i++) {
				Object v = args.get(i);
				if (v == null) {
					ps.setNull(i + 1, Types.OTHER);
				}
				else {
					ps.setObject(i + 1, phpString(v), Types.OTHER);
				}
			}
		}, rs -> rs.next() ? rs.getLong(1) : 0L);
		return n == null ? 0 : n;
	}

	private static String identifier(String name) {
		if (!SQL_IDENTIFIER.matcher(name).matches()) {
			throw new IllegalArgumentException("Gecersiz tablo/kolon adi: " + name);
		}
		return name;
	}

	// ---- mesajlar ----------------------------------------------------------

	/**
	 * {@code getDisplayableAttribute}: normal alanda alt cizgi bosluga cevrilir ("per page"); bir
	 * {@code *} kuralindan genisleyen alan (implicit attribute) oldugu gibi yazilir ("items.0.product_id").
	 */
	private static String message(ParsedRule rule, String attribute, List<ParsedRule> rules, boolean implicit) {
		List<String> p = rule.parameters();
		String template = switch (rule.name()) {
			case "required" -> LaravelMessages.REQUIRED;
			case "required_with" -> "The :attribute field is required when " + String.join(" / ",
				p.stream().map(LaravelMessages::attribute).toList()) + " is present.";
			case "required_without" -> "The :attribute field is required when " + String.join(" / ",
				p.stream().map(LaravelMessages::attribute).toList()) + " is not present.";
			case "prohibits" -> "The :attribute field prohibits " + String.join(" / ",
				p.stream().map(LaravelMessages::attribute).toList()) + " from being present.";
			case "different" -> "The :attribute field and " + LaravelMessages.attribute(p.getFirst()) + " must be different.";
			case "distinct" -> "The :attribute field has a duplicate value.";
			case "present" -> "The :attribute field must be present.";
			case "filled" -> "The :attribute field must have a value.";
			case "string" -> LaravelMessages.STRING;
			case "integer" -> LaravelMessages.INTEGER;
			case "numeric" -> LaravelMessages.NUMERIC;
			case "boolean" -> LaravelMessages.BOOLEAN;
			case "array" -> LaravelMessages.ARRAY;
			case "email" -> LaravelMessages.EMAIL;
			case "in", "not_in", "exists" -> LaravelMessages.IN;
			case "unique" -> LaravelMessages.UNIQUE;
			case "confirmed" -> "The :attribute field confirmation does not match.";
			case "same" -> "The :attribute field must match " + LaravelMessages.attribute(p.getFirst()) + ".";
			case "min" -> switch (sizeType(rules)) {
				case "numeric" -> "The :attribute field must be at least " + p.getFirst() + ".";
				case "array" -> "The :attribute field must have at least " + p.getFirst() + " items.";
				default -> "The :attribute field must be at least " + p.getFirst() + " characters.";
			};
			case "max" -> switch (sizeType(rules)) {
				case "numeric" -> "The :attribute field must not be greater than " + p.getFirst() + ".";
				case "array" -> "The :attribute field must not have more than " + p.getFirst() + " items.";
				default -> "The :attribute field must not be greater than " + p.getFirst() + " characters.";
			};
			case "size" -> switch (sizeType(rules)) {
				case "numeric" -> "The :attribute field must be " + p.getFirst() + ".";
				case "array" -> "The :attribute field must contain " + p.getFirst() + " items.";
				default -> "The :attribute field must be " + p.getFirst() + " characters.";
			};
			case "gt" -> switch (sizeType(rules)) {
				case "numeric" -> "The :attribute field must be greater than " + p.getFirst() + ".";
				case "array" -> "The :attribute field must have more than " + p.getFirst() + " items.";
				default -> "The :attribute field must be greater than " + p.getFirst() + " characters.";
			};
			case "between" -> switch (sizeType(rules)) {
				case "numeric" -> "The :attribute field must be between " + p.get(0) + " and " + p.get(1) + ".";
				case "array" -> "The :attribute field must have between " + p.get(0) + " and " + p.get(1) + " items.";
				default -> "The :attribute field must be between " + p.get(0) + " and " + p.get(1) + " characters.";
			};
			default -> throw new IllegalArgumentException("Mesaji tanimsiz kural: " + rule.name());
		};
		return implicit ? template.replace(":attribute", attribute) : LaravelMessages.format(template, attribute);
	}

	/** {@code getAttributeType}: kurallara gore, degere gore degil. */
	private static String sizeType(List<ParsedRule> rules) {
		if (rules.stream().anyMatch(r -> NUMERIC_RULES.contains(r.name()))) {
			return "numeric";
		}
		if (has(rules, "array") || has(rules, "list")) {
			return "array";
		}
		return "string";
	}

	// ---- genisletme ve yardimcilar -----------------------------------------

	/** {@code roles.*} -> istekteki her eleman icin {@code roles.0}, {@code roles.1}... (kural sirasinda). */
	private static Map<String, List<ParsedRule>> expand(Map<String, List<ParsedRule>> fields, Map<String, Object> data,
		Map<String, String> patterns) {
		Map<String, List<ParsedRule>> out = new LinkedHashMap<>();
		fields.forEach((attribute, rules) -> {
			if (!attribute.contains("*")) {
				out.put(attribute, rules);
				return;
			}
			for (String concrete : concreteKeys(attribute, data)) {
				out.put(concrete, rules);
				patterns.put(concrete, attribute);
			}
		});
		return out;
	}

	private static List<String> concreteKeys(String pattern, Object data) {
		List<String> keys = new ArrayList<>(List.of(""));
		for (String part : pattern.split("\\.")) {
			List<String> next = new ArrayList<>();
			for (String prefix : keys) {
				Object node = prefix.isEmpty() ? data : RequestInput.Paths.value(data, prefix);
				if (part.equals("*")) {
					if (node instanceof List<?> list) {
						for (int i = 0; i < list.size(); i++) {
							next.add(join(prefix, String.valueOf(i)));
						}
					}
					else if (node instanceof Map<?, ?> map) {
						map.keySet().forEach(k -> next.add(join(prefix, String.valueOf(k))));
					}
				}
				else {
					next.add(join(prefix, part));
				}
			}
			keys = next;
		}
		return keys;
	}

	private static String join(String prefix, String part) {
		return prefix.isEmpty() ? part : prefix + "." + part;
	}

	private static boolean has(List<ParsedRule> rules, String name) {
		return rules.stream().anyMatch(r -> r.name().equals(name));
	}

	private static double number(String parameter) {
		return Double.parseDouble(parameter);
	}

	/** PHP {@code (string) $value} */
	static String phpString(Object value) {
		return switch (value) {
			case null -> "";
			case Boolean b -> b ? "1" : "";
			case BigDecimal d -> d.stripTrailingZeros().toPlainString();
			default -> value.toString();
		};
	}

	/** PHP {@code ===}: ayni tip ve deger. */
	private static boolean phpIdentical(Object a, Object b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.getClass() == b.getClass() && a.equals(b);
	}

	/** Ayristirilmis kural: {@code max:255 -> (max, [255])}. */
	record ParsedRule(String name, List<String> parameters) {

		static List<ParsedRule> parse(String rules) {
			List<ParsedRule> out = new ArrayList<>();
			for (String raw : rules.split("\\|")) {
				if (raw.isBlank()) {
					continue;
				}
				int colon = raw.indexOf(':');
				String name = (colon < 0 ? raw : raw.substring(0, colon)).trim().toLowerCase();
				List<String> params = colon < 0 ? List.of() : List.of(raw.substring(colon + 1).split(",", -1));
				out.add(new ParsedRule(name, params));
			}
			return out;
		}

	}

}
