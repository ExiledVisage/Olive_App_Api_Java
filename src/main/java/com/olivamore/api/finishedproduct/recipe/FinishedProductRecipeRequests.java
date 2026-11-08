package com.olivamore.api.finishedproduct.recipe;

import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Laravel {@code Http/Requests/FinishedProductRecipe}: StoreFinishedProductRecipeRequest,
 * UpdateFinishedProductRecipeRequest (kurallar ve {@code withValidator} oran toplami).
 */
final class FinishedProductRecipeRequests {

	private FinishedProductRecipeRequests() {
	}

	static Rules store() {
		return rules("required", "required");
	}

	/** Icerik oranlarinin toplami 1 olmali (icerik verilmese de: toplam 0). */
	static LaravelValidator.After storeAfter() {
		return (data, add) -> ratioTotal(data.containsKey("contents") ? data.get("contents") : List.of(), add);
	}

	static Rules update() {
		return rules("sometimes", "required_with:contents");
	}

	/** Icerik verildiyse oranlarin toplami 1 olmali. */
	static LaravelValidator.After updateAfter() {
		return (data, add) -> {
			if (data.get("contents") != null) {
				ratioTotal(data.get("contents"), add);
			}
		};
	}

	/**
	 * Laravel kurallari; {@code distinct} Java'da eklendi: ayni urun tipi ya da attribute iki kez gelince
	 * Laravel unique kisitina takilip 500 veriyordu (DEVIATIONS).
	 */
	private static Rules rules(String presence, String itemPresence) {
		return Rules.of()
			.field("name", presence + "|string|max:255")
			.field("description", "nullable|string")
			.field("contents", presence + "|array|min:1")
			.field("contents.*.production_product_type_id", itemPresence + "|integer|distinct|exists:production_product_types,id")
			.field("contents.*.ratio", itemPresence + "|numeric|min:0.0001|max:1")
			.field("attribute_rules", "nullable|array")
			.field("attribute_rules.*.attribute_definition_id", "required|integer|distinct|exists:attribute_definitions,id")
			.field("attribute_rules.*.value", "required|numeric|min:0")
			.field("attribute_rules.*.min_deviation", "nullable|numeric|min:0")
			.field("attribute_rules.*.max_deviation", "nullable|numeric|min:0");
	}

	/**
	 * {@code collect($contents)->sum('ratio')}, 4 basamaga yuvarlanmis toplam 1 olmali. Laravel'de sayi olmayan
	 * oran (metin, dizi) toplamada 500 veriyor; burada 0 sayilir, alanin kendi hatasi zaten {@code numeric}.
	 */
	static void ratioTotal(Object contents, java.util.function.BiConsumer<String, String> add) {
		Collection<?> list = switch (contents) {
			case null -> List.of();
			case Map<?, ?> m -> m.values();
			case List<?> l -> l;
			default -> List.of(contents);
		};
		double total = 0;
		for (Object item : list) {
			Object ratio = item instanceof Map<?, ?> m ? m.get("ratio") : null;
			total += switch (ratio) {
				case null -> 0;
				case Number n -> n.doubleValue();
				case Boolean b -> b ? 1 : 0;
				case String s when LaravelValidator.numeric(s) -> Php.floatval(s.strip());
				default -> 0;
			};
		}
		double rounded = Php.round(total, 4);
		if (rounded != 1.0) {
			add.accept("contents", "İçerik oranlarının toplamı 1.00 olmalıdır. Mevcut toplam: " + Php.floatString(rounded));
		}
	}

}
