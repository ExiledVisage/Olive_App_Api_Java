package com.olivamore.api.shared.validation;

import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.web.RequestInput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Veritabani gerektirmeyen kurallar. Beklenen mesajlar Laravel'den yakalandi
 * (src/test/resources/laravel/identity.json) ya da Laravel'in Validator kodundan.
 * unique / exists: IdentityParityIT.
 */
class LaravelValidatorTest {

	private final LaravelValidator validator = new LaravelValidator(null,
		JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build());

	private static final Rules LOGIN = Rules.of()
		.field("email", "required|string|email")
		.field("password", "required|string");

	// ---- Laravel fixture'lari ----------------------------------------------

	@Test
	void loginWithNumbers() {
		// login email sayi: bir alanda birden fazla kural duser, kural sirasiyla
		assertErrors(input("email", 5L, "password", 5L), LOGIN,
			"The email field must be a string. (and 2 more errors)",
			Map.of("email", List.of("The email field must be a string.", "The email field must be a valid email address."),
				"password", List.of("The password field must be a string.")));
	}

	@Test
	void requiredStopsTheRest() {
		// login email null: required duser, string/email calismaz
		Map<String, Object> nulls = new HashMap<>();
		nulls.put("email", null);
		nulls.put("password", null);
		assertErrors(RequestInput.of(nulls), LOGIN, "The email field is required. (and 1 more error)",
			Map.of("email", List.of("The email field is required."), "password", List.of("The password field is required.")));
		assertErrors(RequestInput.of(Map.of()), LOGIN, "The email field is required. (and 1 more error)",
			Map.of("email", List.of("The email field is required."), "password", List.of("The password field is required.")));
	}

	@Test
	void emailFormat() {
		assertErrors(input("email", "abc", "password", "x"), LOGIN, "The email field must be a valid email address.",
			Map.of("email", List.of("The email field must be a valid email address.")));
		validator.validate(input("email", "a@b", "password", "x"), LOGIN);
		// Laravel uzunluk siniri uygulamiyor (register uzun: 250 karakterlik yerel kisim gecti)
		validator.validate(input("email", "a".repeat(250) + "@b.co", "password", "x"), LOGIN);
	}

	@Test
	void minAndConfirmedOnSameField() {
		// register hatali: password -> min:8 ve confirmed birlikte
		Rules rules = Rules.of().field("name", "required|string|max:255").field("password", "required|string|min:8|confirmed");
		assertErrors(input("name", 5L, "password", "kisa", "password_confirmation", "farkli"), rules,
			"The name field must be a string. (and 2 more errors)",
			Map.of("name", List.of("The name field must be a string."),
				"password", List.of("The password field must be at least 8 characters.", "The password field confirmation does not match.")));
	}

	@Test
	void maxCountsCharacters() {
		Rules rules = Rules.of().field("name", "required|string|max:255");
		assertErrors(input("name", "x".repeat(256)), rules, "The name field must not be greater than 255 characters.",
			Map.of("name", List.of("The name field must not be greater than 255 characters.")));
		validator.validate(input("name", "ş".repeat(255)), rules); // mb_strlen: karakter, bayt degil
	}

	@Test
	void greaterThanLiteral() {
		// StoreStorageUnitRequest capacity: numeric|gt:0 (Laravel: sayisal olmayan deger gt'de de kalir)
		Rules rules = Rules.of().field("capacity", "required|numeric|gt:0");
		assertErrors(input("capacity", 0L), rules, "The capacity field must be greater than 0.",
			Map.of("capacity", List.of("The capacity field must be greater than 0.")));
		assertErrors(input("capacity", "abc"), rules, "The capacity field must be a number. (and 1 more error)",
			Map.of("capacity", List.of("The capacity field must be a number.", "The capacity field must be greater than 0.")));
		assertThat(validator.validate(input("capacity", "0.01"), rules).get("capacity")).isEqualTo("0.01");
	}

	@Test
	void requiredWithoutAllReplacesAsterisksInParameters() {
		// UpdateBatchProcessRequest: her cikti icin urun uc yoldan biriyle; "*" elemanin indisiyle dolar ve
		// genisleyen alan adlari mesajda oldugu gibi yazilir
		String o = "outputs.*.";
		Rules rules = Rules.of()
			.field("outputs.*.type_id", "required_without_all:" + o + "product_id," + o + "key|nullable|integer")
			.field("outputs.*.product_id", "nullable|integer")
			.field("outputs.*.key", "nullable|string");
		assertErrors(input("outputs", List.of(Map.of("q", 1L), Map.of("key", "olive_oil"))), rules,
			"The outputs.0.type_id field is required when none of outputs.0.product_id / outputs.0.key are present.",
			Map.of("outputs.0.type_id", List.of(
				"The outputs.0.type_id field is required when none of outputs.0.product_id / outputs.0.key are present.")));
	}

	@Test
	void customMessagesReplaceRuleMessages() {
		// FormRequest messages(): "alan.kural"; "*"'li alanlarda desen adiyla
		Rules rules = Rules.of()
			.field("delta", "required|integer|not_in:0")
			.field("rows.*.q", "required")
			.message("delta.not_in", "Düzeltme miktarı sıfır olamaz.")
			.message("rows.*.q.required", "Miktar gerekli.");
		assertThatThrownBy(() -> validator.validate(input("delta", "0", "rows", List.of(Map.of())), rules))
			.isInstanceOfSatisfying(LaravelValidationException.class, e -> assertThat(e.errors()).isEqualTo(Map.of(
				"delta", List.of("Düzeltme miktarı sıfır olamaz."), "rows.0.q", List.of("Miktar gerekli."))));
	}

	@Test
	void afterHookAddsErrorsBehindRuleErrors() {
		Rules rules = Rules.of().field("a", "required").field("b", "nullable");
		assertThatThrownBy(() -> validator.validate(input("b", "x"), rules, (data, add) -> add.accept("b", "b gecersiz.")))
			.isInstanceOfSatisfying(com.olivamore.api.shared.error.LaravelValidationException.class, e ->
				assertThat(e.errors().keySet()).containsExactly("a", "b"));
		assertThat(validator.validate(input("a", 1L), rules, (data, add) -> { }).get("a")).isEqualTo(1L);
	}

	@Test
	void wildcardExpandsToElements() {
		// exists yerine in (veritabani yok). Fark: onceki hata yalnizca unique/exists'i atlatir, in calisir.
		// (exists ile Laravel'in "roles.0 tek hata" davranisi IdentityParityIT'de.)
		Rules rules = Rules.of().field("roles", "nullable|array").field("roles.*", "string|in:planner,operator");
		assertErrors(input("roles", List.of(5L, "yok", "planner")), rules, "The roles.0 field must be a string. (and 2 more errors)",
			Map.of("roles.0", List.of("The roles.0 field must be a string.", "The selected roles.0 is invalid."),
				"roles.1", List.of("The selected roles.1 is invalid.")));
		assertErrors(input("roles", "planner"), rules, "The roles field must be an array.",
			Map.of("roles", List.of("The roles field must be an array.")));
	}

	@Test
	void arrayMinUsesItemMessage() {
		// user roles bos: required bos diziyi reddeder ve min'e gecmez
		Rules rules = Rules.of().field("roles", "required|array|min:1").field("roles.*", "string");
		assertErrors(input("roles", List.of()), rules, "The roles field is required.",
			Map.of("roles", List.of("The roles field is required.")));
		Rules min2 = Rules.of().field("roles", "array|min:2");
		assertErrors(input("roles", List.of("a")), min2, "The roles field must have at least 2 items.",
			Map.of("roles", List.of("The roles field must have at least 2 items.")));
	}

	// ---- Validator::passes() kurallari -------------------------------------

	@Test
	void absentFieldSkipsNonImplicitRules() {
		validator.validate(RequestInput.of(Map.of()), Rules.of().field("name", "string|max:3"));
	}

	@Test
	void nullableSkipsNull() {
		Map<String, Object> data = new HashMap<>();
		data.put("password", null);
		validator.validate(RequestInput.of(data), Rules.of().field("password", "nullable|string|min:8"));
		// nullable olmadan: alan var ve null -> string duser, min (uzunluk 0) duser
		assertErrors(RequestInput.of(data), Rules.of().field("password", "string|min:8"),
			"The password field must be a string. (and 1 more error)",
			Map.of("password", List.of("The password field must be a string.", "The password field must be at least 8 characters.")));
	}

	@Test
	void emptyStringBecomesNull() {
		// ConvertEmptyStringsToNull: "" ve "   " -> null -> required
		assertErrors(input("email", "   ", "password", ""), LOGIN, "The email field is required. (and 1 more error)",
			Map.of("email", List.of("The email field is required."), "password", List.of("The password field is required.")));
	}

	@Test
	void bailStopsAtFirstFailure() {
		assertErrors(input("name", 5L), Rules.of().field("name", "bail|string|min:3"), "The name field must be a string.",
			Map.of("name", List.of("The name field must be a string.")));
	}

	@Test
	void sizeTypeFollowsRules() {
		Rules numeric = Rules.of().field("qty", "integer|min:1|max:10");
		assertErrors(input("qty", 11L), numeric, "The qty field must not be greater than 10.",
			Map.of("qty", List.of("The qty field must not be greater than 10.")));
		assertErrors(input("qty", "0"), numeric, "The qty field must be at least 1.",
			Map.of("qty", List.of("The qty field must be at least 1.")));
	}

	@Test
	void integerFollowsFilterVar() {
		Rules rules = Rules.of().field("n", "integer");
		validator.validate(input("n", 5L), rules);
		validator.validate(input("n", "5"), rules);
		validator.validate(input("n", "+5"), rules);
		validator.validate(input("n", new BigDecimal("5.0")), rules);
		for (Object bad : List.of("05", "5.5", new BigDecimal("5.5"), "abc", List.of(1L))) {
			assertThatThrownBy(() -> validator.validate(input("n", bad), rules)).as(String.valueOf(bad))
				.isInstanceOf(LaravelValidationException.class);
		}
	}

	@Test
	void validatedKeepsOnlyRuledFields() {
		Rules rules = Rules.of().field("name", "required|string").field("roles", "nullable|array").field("roles.*", "string");
		ValidatedInput v = validator.validate(input("name", " Ali ", "roles", List.of("a", "b"), "is_admin", true), rules);
		assertThat(v.all()).containsExactly(Map.entry("name", "Ali"), Map.entry("roles", List.of("a", "b")));
		assertThat(v.has("is_admin")).isFalse();
	}

	@Test
	void validatedDropsUnruledNestedKeys() {
		// excludeUnvalidatedArrayKeys: items.*.qty kurali varsa items'in diger anahtarlari alinmaz
		Rules rules = Rules.of().field("items", "required|array").field("items.*.qty", "required|integer");
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("qty", 2L);
		item.put("price", 9L);
		ValidatedInput v = validator.validate(input("items", List.of(item)), rules);
		assertThat(v.all()).isEqualTo(Map.of("items", List.of(Map.of("qty", 2L))));
	}

	@Test
	void passwordIsNotTrimmed() {
		ValidatedInput v = validator.validate(input("password", " bosluklu ", "name", " Ali "),
			Rules.of().field("password", "required|string").field("name", "required|string"));
		assertThat(v.string("password")).isEqualTo(" bosluklu ");
		assertThat(v.string("name")).isEqualTo("Ali");
	}

	// ---- yardimcilar -------------------------------------------------------

	private static RequestInput input(Object... keyValues) {
		Map<String, Object> data = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			data.put((String) keyValues[i], keyValues[i + 1]);
		}
		return RequestInput.of(data);
	}

	private void assertErrors(RequestInput input, Rules rules, String message, Map<String, List<String>> errors) {
		assertThatThrownBy(() -> validator.validate(input, rules))
			.isInstanceOfSatisfying(LaravelValidationException.class, e -> {
				assertThat(e.getMessage()).isEqualTo(message);
				assertThat(e.errors()).containsExactlyInAnyOrderEntriesOf(errors);
				// alan sirasi Laravel'deki gibi kural sirasi
				assertThat(List.copyOf(e.errors().keySet())).isEqualTo(
					Arrays.stream(errors.keySet().toArray(String[]::new)).sorted((a, b) ->
						Integer.compare(order(rules, a), order(rules, b))).toList());
			});
	}

	private static int order(Rules rules, String attribute) {
		List<String> keys = List.copyOf(rules.fields().keySet());
		String base = attribute.contains(".") ? attribute.substring(0, attribute.indexOf('.')) + ".*" : attribute;
		int index = keys.indexOf(base);
		return index * 1000 + (attribute.contains(".") ? Integer.parseInt(attribute.substring(attribute.indexOf('.') + 1)) : 0);
	}

}
