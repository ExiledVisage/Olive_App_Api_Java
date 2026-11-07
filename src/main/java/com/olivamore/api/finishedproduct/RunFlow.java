package com.olivamore.api.finishedproduct;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.packaging.PackagingAllocations;
import com.olivamore.api.packaging.PackagingAllocations.Need;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Laravel {@code FinishedProductRunService}: dolum kosusunun planlanmasi (tank ve tarif kontrolleri, materyal
 * payi, tahmini attribute degerleri, ambalaj tahsisi), durum gecisleri ve tamamlama (ambalaj ve yag tuketimi).
 * Hesaplar PHP float (double); veritabanina 14 basamak.
 */
@Component
class RunFlow {

	private static final double RATIO_TOLERANCE = 0.02;
	/** Dolumda cekilen yagin sise icerigini en fazla bu oranda gecmesine izin verilir (hat ici fire). */
	private static final double FILL_LOSS_TOLERANCE = 0.02;
	private static final double EPSILON = 0.00005;

	private final Db db;
	private final PackagingAllocations packaging;
	private final StockOperations stock;

	RunFlow(Db db, PackagingAllocations packaging, StockOperations stock) {
		this.db = db;
		this.packaging = packaging;
		this.stock = stock;
	}

	// ---- planlama --------------------------------------------------------------

	/**
	 * {@code create}. @return yeni kosu id
	 * @param sourceQuantity {@code source.quantity} (verilmezse sise adedi x hacim, fire payiyla)
	 */
	long create(long productId, Object quantity, Object note, long storageUnitId, Object sourceQuantity, Object lossPercent) {
		Row product = db.find("finished_products", productId).orElseThrow(() -> new ModelNotFoundException("FinishedProduct", productId));
		Row recipe = db.find("finished_product_recipes", product.longValue("finished_product_recipe_id")).orElse(null);
		List<Row> contents = recipe == null ? List.of()
			: db.whereIn("finished_product_recipe_contents", "finished_product_recipe_id", List.of(recipe.id()), null);
		if (recipe == null || contents.isEmpty()) {
			throw new BusinessRuleException("'" + product.string("name") + "' ürününün geçerli bir formülü yok; formülsüz dolum yapılamaz.");
		}
		Row unit = db.find("storage_units", storageUnitId).orElseThrow(() -> new ModelNotFoundException("StorageUnit", storageUnitId));
		Tank tank = loadTank(unit.id());
		if (tank.materials().isEmpty()) {
			throw new BusinessRuleException("'" + unit.string("code") + "' tankı boş.");
		}
		long bottles = Php.intval(quantity);
		double required = requiredSourceQuantity(product, bottles, 0.0, tank);
		double requested = sourceQuantity != null ? Php.floatval(sourceQuantity)
			: requiredSourceQuantity(product, bottles, lossPercent == null ? 0 : Php.floatval(lossPercent), tank);

		assertSufficientQuantity(tank, requested);
		assertTankMatchesRecipe(tank, recipe, contents);
		assertSourceQuantityMatchesBottles(product, bottles, requested, required, tank);

		Map<String, Object> values = new LinkedHashMap<>();
		values.put("finished_product_id", product.id());
		values.put("finished_product_recipe_id", recipe.id());
		values.put("source_storage_unit_id", unit.id());
		values.put("quantity", quantity);
		values.put("status", "planned");
		values.put("note", note);
		long runId = db.insert("finished_product_runs", values);

		createRunMaterials(runId, tank, requested);
		createEstimatedAttributeValues(runId, tank, recipe);
		packaging.allocateForRun(runId, needs(product.id(), bottles));
		return runId;
	}

	/** Tanktaki aktif materyaller (fiziksel sira) ve Laravel'in yukledigi iliskileri. */
	private record Tank(List<Row> materials, Map<Long, Row> types, Map<Long, Row> products, Map<Long, List<Row>> attributeValues,
		Map<Long, Row> definitions) {

		double total() {
			double total = 0;
			for (Row m : materials) {
				total += Php.floatval(Casts.decimal(m.get("quantity"), 4));
			}
			return total;
		}

		Row type(Row material) {
			Long id = material.longValue("production_product_type_id");
			return id == null ? null : types.get(id);
		}

	}

	private Tank loadTank(long unitId) {
		List<Row> materials = db.select("select * from materials where current_location_type = ? and current_location_id = ? and status = ?",
			"storage_unit", unitId, "active");
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(materials, "production_product_type_id"), null));
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(List.copyOf(types.values()), "production_product_id"), null));
		List<Row> values = db.whereIn("material_attribute_values", "material_id", Relations.ids(materials), null);
		Map<Long, Row> definitions = Relations.byId(db.whereIn("production_product_attribute_definitions", "id",
			Relations.column(values, "production_product_attribute_definition_id"), null));
		return new Tank(materials, types, products, Relations.groupBy(values, "material_id"), definitions);
	}

	private static void assertSufficientQuantity(Tank tank, double requested) {
		double total = tank.total();
		if (requested > total) {
			throw new BusinessRuleException("Tankta yeterli miktar yok. Mevcut: " + Php.floatString(total) + ", İstenen: "
				+ Php.floatString(requested) + ".");
		}
	}

	/** Tankta tarifte olmayan urun tolerans ustunde olmamali; tarifteki her icerik beklenen oranin +-%2'sinde olmali. */
	private void assertTankMatchesRecipe(Tank tank, Row recipe, List<Row> contents) {
		double total = tank.total();
		Set<Long> recipeTypes = new LinkedHashSet<>();
		for (Row c : contents) {
			recipeTypes.add(c.longValue("production_product_type_id"));
		}
		List<Row> foreign = tank.materials().stream()
			.filter(m -> !recipeTypes.contains(m.longValue("production_product_type_id"))).toList();
		if (!foreign.isEmpty() && total > 0) {
			double foreignQuantity = 0;
			for (Row m : foreign) {
				foreignQuantity += Php.floatval(Casts.decimal(m.get("quantity"), 4));
			}
			double ratio = foreignQuantity / total;
			if (ratio > RATIO_TOLERANCE + EPSILON) {
				Set<String> names = new LinkedHashSet<>();
				for (Row m : foreign) {
					Row type = tank.type(m);
					names.add(type != null ? type.string("name") : "ID:" + m.get("production_product_type_id"));
				}
				throw new BusinessRuleException("Tankta tarifte olmayan ürün var: " + String.join(", ", names) + " (%"
					+ Php.floatString(Php.round(ratio * 100, 1)) + "). '" + recipe.string("name") + "' tarifi bu tanktan doldurulamaz.");
			}
		}
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(contents, "production_product_type_id"), null));
		for (Row content : contents) {
			long typeId = content.longValue("production_product_type_id");
			double expected = Php.floatval(Casts.decimal(content.get("ratio"), 4));
			double actual = 0;
			for (Row m : tank.materials()) {
				if (Objects.equals(m.longValue("production_product_type_id"), typeId)) {
					actual += Php.floatval(Casts.decimal(m.get("quantity"), 4));
				}
			}
			double actualRatio = total > 0 ? actual / total : 0;
			if (Math.abs(actualRatio - expected) > RATIO_TOLERANCE + EPSILON) {
				Row type = types.get(typeId);
				String name = type != null ? type.string("name") : "ID:" + typeId;
				throw new BusinessRuleException("Tank blend oranı tarife uymuyor. '" + name + "' için beklenen oran: "
					+ Php.floatString(Php.round(expected * 100, 1)) + "%, tankta bulunan: "
					+ Php.floatString(Php.round(actualRatio * 100, 1)) + "%.");
			}
		}
	}

	/** Tanktan cekilecek miktar, tankin (ilk materyalin) biriminde: adet x hacim litre, fire payiyla, 4 basamak. */
	private static double requiredSourceQuantity(Row product, long bottles, double lossPercent, Tank tank) {
		double litres = bottles * Php.floatval(Casts.decimal(product.get("volume_litre"), 4));
		litres *= 1 + (lossPercent / 100);
		Row first = tank.materials().getFirst();
		Row type = tank.type(first);
		return Php.round(convert(litres, "LITRE", first.string("unit"), type,
			type == null || type.longValue("production_product_id") == null ? null : tank.products().get(type.longValue("production_product_id"))), 4);
	}

	/** {@code UnitConverter::convert}: ayni birimse oldugu gibi, yoksa yogunlukla (kg = litre x yogunluk). */
	static double convert(double quantity, String from, String to, Row type, Row product) {
		if (Objects.equals(from, to)) {
			return quantity;
		}
		Double density = type == null ? null : RunReads.effectiveDensity(type, product);
		if (density == null) {
			String name = type != null && type.get("name") != null ? type.string("name") : "Bu ürün tipi";
			throw new BusinessRuleException(name + " için yoğunluk tanımlı değil; " + from + " ile " + to
				+ " arasında çevrim yapılamaz. Ürün tipine yoğunluk (kg/L) girilmeli.");
		}
		if ("LITRE".equals(from) && "KG".equals(to)) {
			return quantity * density;
		}
		if ("KG".equals(from) && "LITRE".equals(to)) {
			return quantity / density;
		}
		throw new BusinessRuleException("Desteklenmeyen birim çevrimi: " + from + " -> " + to + ".");
	}

	/** {@code UnitConverter::label} */
	private static String label(String unit) {
		return switch (unit) {
			case "LITRE" -> "L";
			case "KG" -> "kg";
			case null -> "null";
			default -> unit;
		};
	}

	/** Sise adedi x hacim, tanktan cekilecek miktarla uyusmali (eksik olamaz, fazlasi %2 fireyi asamaz). */
	private static void assertSourceQuantityMatchesBottles(Row product, long bottles, double source, double required, Tank tank) {
		double volume = Php.floatval(Casts.decimal(product.get("volume_litre"), 4));
		String unit = label(tank.materials().isEmpty() ? "LITRE" : Objects.requireNonNullElse(tank.materials().getFirst().string("unit"), "LITRE"));
		String head = bottles + " adet × " + Php.floatString(volume) + " L = " + Php.floatString(required) + " " + unit + " yağ ";
		if (source + EPSILON < required) {
			throw new BusinessRuleException(head + "gerekiyor, tanktan çekilecek miktar " + Php.floatString(source) + " " + unit + ".");
		}
		double max = required * (1 + FILL_LOSS_TOLERANCE);
		if (source > max + EPSILON) {
			throw new BusinessRuleException(head + "yeterli, tanktan çekilecek miktar " + Php.floatString(source) + " " + unit
				+ ". Fark, %" + Php.floatString(FILL_LOSS_TOLERANCE * 100) + " fire toleransını aşıyor (en fazla "
				+ Php.floatString(Php.round(max, 4)) + " " + unit + ").");
		}
	}

	/** Her materyalden tanktaki payi oraninda (4 basamak); sifir pay yazilmaz. */
	private void createRunMaterials(long runId, Tank tank, double requested) {
		double tankTotal = tank.total();
		for (Row material : tank.materials()) {
			double proportion = tankTotal > 0 ? Php.floatval(Casts.decimal(material.get("quantity"), 4)) / tankTotal : 0;
			double used = Php.round(proportion * requested, 4);
			if (used <= 0) {
				continue;
			}
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("source_material_id", material.id());
			values.put("quantity_used", Php.floatString(used));
			values.put("finished_product_run_id", runId);
			db.insert("finished_product_run_materials", values);
		}
	}

	/**
	 * Tarifin her attribute kurali icin tanktaki materyallerin agirlikli ortalamasi; kabul araliginin disindaysa
	 * kosu planlanmaz. Degeri olmayan kural atlanir.
	 */
	private void createEstimatedAttributeValues(long runId, Tank tank, Row recipe) {
		List<Row> rules = db.whereIn("finished_product_recipe_attribute_rules", "finished_product_recipe_id", List.of(recipe.id()), null);
		Map<Long, Row> definitions = Relations.byId(db.whereIn("attribute_definitions", "id",
			Relations.column(rules, "attribute_definition_id"), null));
		for (Row rule : rules) {
			long definitionId = rule.longValue("attribute_definition_id");
			double weightedSum = 0;
			double weightedTotal = 0;
			for (Row material : tank.materials()) {
				Row value = null;
				for (Row v : tank.attributeValues().getOrDefault(material.id(), List.of())) {
					Row definition = v.longValue("production_product_attribute_definition_id") == null ? null
						: tank.definitions().get(v.longValue("production_product_attribute_definition_id"));
					if (definition != null && Objects.equals(definition.longValue("attribute_definition_id"), definitionId)) {
						value = v;
						break;
					}
				}
				if (value == null || value.get("value_decimal") == null) {
					continue;
				}
				double qty = Php.floatval(Casts.decimal(material.get("quantity"), 4));
				weightedSum += Php.floatval(value.get("value_decimal")) * qty;
				weightedTotal += qty;
			}
			if (weightedTotal <= 0) {
				continue;
			}
			double estimated = Php.round(weightedSum / weightedTotal, 4);
			Double min = rule.get("min_deviation") == null ? null
				: Php.floatval(Casts.decimal(rule.get("value"), 4)) - Php.floatval(Casts.decimal(rule.get("min_deviation"), 4));
			Double max = rule.get("max_deviation") == null ? null
				: Php.floatval(Casts.decimal(rule.get("value"), 4)) + Php.floatval(Casts.decimal(rule.get("max_deviation"), 4));
			Row definition = definitions.get(definitionId);
			String label = definition == null || definition.get("label") == null ? "" : definition.string("label");
			if (min != null && estimated < min) {
				throw new BusinessRuleException("Tahmini " + label + " değeri (" + Php.floatString(estimated) + ") "
					+ "kabul aralığının altında. Min: " + Php.floatString(min) + ".");
			}
			if (max != null && estimated > max) {
				throw new BusinessRuleException("Tahmini " + label + " değeri (" + Php.floatString(estimated) + ") "
					+ "kabul aralığının üstünde. Max: " + Php.floatString(max) + ".");
			}
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("attribute_definition_id", definitionId);
			values.put("estimated_value", Php.floatString(estimated));
			values.put("actual_value", null);
			values.put("finished_product_run_id", runId);
			db.insert("finished_product_run_attribute_values", values);
		}
	}

	/** Urunun ambalaj bilesenleri (fiziksel sira): kalem basina kosu adedi x bilesen adedi. */
	List<Need> needs(long productId, long bottles) {
		List<Need> needs = new ArrayList<>();
		for (Row c : db.whereIn("finished_product_components", "finished_product_id", List.of(productId), null)) {
			needs.add(new Need(c.longValue("packaging_item_id"), bottles * Php.intval(c.get("quantity"))));
		}
		return needs;
	}

	// ---- durum -------------------------------------------------------------------

	void updateStatus(Row run, String status, long userId) {
		String current = run.string("status");
		if (!RunReads.TRANSITIONS.getOrDefault(current, List.of()).contains(status)) {
			throw new BusinessRuleException("'" + current + "' durumundan '" + status + "' durumuna geçiş yapılamaz.");
		}
		Map<String, Object> changes = new LinkedHashMap<>();
		switch (status) {
			case "in_progress" -> {
				changes.put("status", "in_progress");
				changes.put("started_at", Timestamps.now());
				db.update("finished_product_runs", run.id(), changes);
			}
			case "completed" -> complete(run, userId);
			default -> db.update("finished_product_runs", run.id(), Map.of("status", status));
		}
	}

	/**
	 * Once her sey dogrulanir (ambalaj, tanktaki materyaller), sonra stok degisir: urun stogu, ambalaj
	 * lotlari, tanktan cikis.
	 */
	private void complete(Row run, long userId) {
		long productId = run.longValue("finished_product_id");
		long bottles = Php.intval(run.get("quantity"));
		List<Need> needs = needs(productId, bottles);
		List<Row> runMaterials = db.whereIn("finished_product_run_materials", "finished_product_run_id", List.of(run.id()), null);
		Map<Long, Row> materials = Relations.byId(db.whereIn("materials", "id", Relations.column(runMaterials, "source_material_id"), null));

		assertPackagingAvailable(run.id(), needs);
		assertRunMaterialsStillAvailable(runMaterials, materials);

		db.execute("update finished_products set total_stock = total_stock + ?, updated_at = ? where id = ?",
			bottles, Timestamps.now(), productId);
		packaging.consumeForRun(run.id(), needs, userId);
		for (Row runMaterial : runMaterials) {
			Row material = materials.get(runMaterial.longValue("source_material_id"));
			long unitId = material.longValue("current_location_id");
			Row unit = db.find("storage_units", unitId).orElseThrow(() -> new ModelNotFoundException("StorageUnit", unitId));
			stock.outboundForRun(unit, material, Php.floatval(Casts.decimal(runMaterial.get("quantity_used"), 4)),
				"Dolum Run #" + run.id(), run.id());
		}
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("status", "completed");
		changes.put("completed_at", Timestamps.now());
		db.update("finished_product_runs", run.id(), changes);
	}

	/** Kalem basina eldeki (aktif lot) adet yeter mi; tahsis varsa hala gecerli mi. */
	private void assertPackagingAvailable(long runId, List<Need> needs) {
		List<String> shortages = new ArrayList<>();
		for (Need need : needs) {
			long available = packaging.availableQuantity(need.itemId());
			if (available < need.quantity()) {
				String name = db.find("packaging_items", need.itemId()).map(i -> i.string("name")).orElse("");
				shortages.add(name + " (gerekli " + need.quantity() + ", lotlarda " + available + ")");
			}
		}
		if (!shortages.isEmpty()) {
			throw new BusinessRuleException("Ambalaj stoğu yetersiz: " + String.join("; ", shortages) + ".");
		}
		if (db.count("select count(*) from finished_product_run_packagings where finished_product_run_id = ?", runId) > 0) {
			packaging.assertAllocationStillAvailable(runId, needs);
		}
	}

	/** Planlamadan sonra tanktaki materyal tasinmis, tuketilmis ya da azalmis olabilir. */
	private void assertRunMaterialsStillAvailable(List<Row> runMaterials, Map<Long, Row> materials) {
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(List.copyOf(materials.values()), "production_product_type_id"), null));
		List<String> problems = new ArrayList<>();
		for (Row runMaterial : runMaterials) {
			Row material = materials.get(runMaterial.longValue("source_material_id"));
			double needed = Php.floatval(Casts.decimal(runMaterial.get("quantity_used"), 4));
			if (material == null) {
				problems.add("materyal #" + runMaterial.get("source_material_id") + " bulunamadı");
				continue;
			}
			Row type = material.longValue("production_product_type_id") == null ? null : types.get(material.longValue("production_product_type_id"));
			String label = (type != null && type.get("name") != null ? type.string("name") : "materyal") + " #" + material.id();
			if (!"active".equals(material.string("status"))) {
				problems.add(label + " artık aktif değil (" + material.string("status") + ")");
				continue;
			}
			if (!"storage_unit".equals(material.string("current_location_type")) || material.longValue("current_location_id") == null
				|| material.longValue("current_location_id") == 0) {
				problems.add(label + " artık bir tankta değil");
				continue;
			}
			String quantity = Casts.decimal(material.get("quantity"), 4);
			if (Php.floatval(quantity) + EPSILON < needed) {
				problems.add(label + " içinde " + quantity + " kaldı, " + Php.floatString(needed) + " gerekiyor");
			}
		}
		if (!problems.isEmpty()) {
			throw new BusinessRuleException("Koşu planlandıktan sonra tank içeriği değişmiş, tamamlanamıyor: "
				+ String.join("; ", problems) + ". Koşuyu iptal edip yeniden planlayın.");
		}
	}

}
