package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Laravel {@code StorageMovementService} + {@code StorageBalanceService}: tanka giris (acilis, yeni
 * materyal, var olan materyal), cikis, tanklar arasi transfer, sayim duzeltmesi ve tank bakiyesi.
 * <p>
 * Kurallar Laravel'deki sirayla: miktar, birim, izinli urun/tip, kapasite, tank uyumu (farkli urun/tip,
 * attribute kurallari ve agirlikli ortalama), materyal bolme. Hesaplar PHP float'iyla (double),
 * veritabanina PHP'nin yazdigi 14 basamakla.
 * <p>
 * Laravel'den fark (DEVIATIONS): islemdeki tanklar transaction basinda {@code FOR UPDATE} ile kilitlenir;
 * ayni tanka eszamanli iki giris kapasiteyi birlikte asamaz. Cevaplar degismez.
 */
@Service
public class StorageMovementService {

	static final String SOURCE_BATCH_PROCESS = "BATCH_PROCESS";
	static final String SOURCE_OPENING = "STORAGE_OPENING";
	static final String SOURCE_INTERNAL_TRANSFER = "INTERNAL_TRANSFER";
	static final String SOURCE_ADJUSTMENT = "STORAGE_ADJUSTMENT";
	static final String SOURCE_MANUAL = "MANUAL";

	static final String TYPE_IN = "IN";
	static final String TYPE_OUT = "OUT";
	static final String TYPE_TRANSFER_IN = "TRANSFER_IN";
	static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";
	static final String TYPE_OPENING = "OPENING";
	static final String TYPE_ADJUSTMENT_IN = "ADJUSTMENT_IN";
	static final String TYPE_ADJUSTMENT_OUT = "ADJUSTMENT_OUT";

	/** materials.quantity decimal(14,4): 4 haneden kucuk farklar gurultu. */
	static final double QUANTITY_EPSILON = 0.00005;

	private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final DateTimeFormatter REFERENCE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Db db;

	StorageMovementService(Db db) {
		this.db = db;
	}

	/** Transfer sonucu: iki hareket ve ortak referans. */
	public record Transfer(String reference, Row out, Row in) {
	}

	// ======================================================================
	// Islemler
	// ======================================================================

	@Transactional
	Row opening(Row unit, Row type, double quantity, String unitName, List<Map<String, Object>> attributeValues, String note) {
		return receiveNewMaterial(unit, type, quantity, unitName, attributeValues, null, note, SOURCE_OPENING, TYPE_OPENING);
	}

	/** Yeni materyal girisi; parti prosesi verilirse urun tipi partinin ciktilarindan olmali. */
	@Transactional
	Row inboundFromType(Row unit, Row type, double quantity, String unitName, List<Map<String, Object>> attributeValues,
		Row batchProcess, String note) {
		if (batchProcess != null) {
			assertTypeIsBatchOutput(batchProcess, type);
		}
		return receiveNewMaterial(unit, type, quantity, unitName == null ? (String) type.get("unit") : unitName,
			attributeValues, batchProcess, note, batchProcess != null ? SOURCE_BATCH_PROCESS : SOURCE_MANUAL, TYPE_IN);
	}

	/** Var olan bir materyalin (ya da bir kisminin) tanka alinmasi. */
	@Transactional
	public Row inbound(Row unit, Row material, double quantity, Row batchProcess, String note) {
		assertPositiveQuantity(quantity);
		lock(unit.id());
		unit = reload(unit);
		Row type = typeOf(material);
		assertProductionProductAllowed(unit, type);
		assertProductionProductTypeAllowed(unit, type);
		assertCapacityAvailable(unit, quantity, Php.strval(material.get("unit")));
		assertCompatibleWithTank(unit, material, quantity);
		Row moving = extractPortion(material, quantity, batchProcess);
		db.update("materials", moving.id(), location("storage_unit", unit.id()));
		Row movement = createMovement(unit.id(), moving, batchProcess, null,
			batchProcess != null ? SOURCE_BATCH_PROCESS : SOURCE_MANUAL, TYPE_IN, null, quantity, note, false, null);
		recalculate(unit, moving);
		return movement;
	}

	/** Tanktan cikan miktar tuketilmis sayilir (consumed). */
	@Transactional
	public Row outbound(Row unit, Row material, double quantity, Row batchProcess, String note) {
		return outbound(unit, material, quantity, batchProcess, note, null);
	}

	/** @param runId dolum kosusu ({@code finished_product_run_id}); yoksa null */
	@Transactional
	public Row outbound(Row unit, Row material, double quantity, Row batchProcess, String note, Long runId) {
		assertPositiveQuantity(quantity);
		lock(unit.id());
		assertMaterialInsideStorage(unit, material);
		Row moving = extractPortion(material, quantity, batchProcess);
		db.update("materials", moving.id(), closed(moving, "consumed", batchProcess));
		Row movement = createMovement(unit.id(), moving, batchProcess, null,
			batchProcess != null ? SOURCE_BATCH_PROCESS : SOURCE_MANUAL, TYPE_OUT, null, quantity, note, true, runId);
		recalculate(unit, moving);
		return movement;
	}

	@Transactional
	public Transfer transfer(Row from, Row to, Row material, double quantity, Row batchProcess, String note) {
		assertPositiveQuantity(quantity);
		if (from.id() == to.id()) {
			throw new BusinessRuleException("Aynı tanktan aynı tanka transfer yapılamaz.");
		}
		lock(from.id(), to.id());
		to = reload(to);
		Row type = typeOf(material);
		assertMaterialInsideStorage(from, material);
		assertProductionProductAllowed(to, type);
		assertProductionProductTypeAllowed(to, type);
		assertCapacityAvailable(to, quantity, Php.strval(material.get("unit")));
		assertCompatibleWithTank(to, material, quantity);
		Row moving = extractPortion(material, quantity, batchProcess);
		String reference = "TRF-" + REFERENCE_TIME.format(Timestamps.now()) + "-" + random(6).toUpperCase(java.util.Locale.ROOT);
		Row out = createMovement(from.id(), moving, batchProcess, to.id(), SOURCE_INTERNAL_TRANSFER, TYPE_TRANSFER_OUT,
			reference, quantity, note, false, null);
		db.update("materials", moving.id(), location("storage_unit", to.id()));
		Row in = createMovement(to.id(), moving, batchProcess, from.id(), SOURCE_INTERNAL_TRANSFER, TYPE_TRANSFER_IN,
			reference, quantity, note, false, null);
		recalculate(from, moving);
		recalculate(to, moving);
		return new Transfer(reference, out, in);
	}

	@Transactional
	Row adjustmentIncrease(Row unit, Row type, double quantity, String unitName, List<Map<String, Object>> attributeValues,
		Row batchProcess, String note) {
		return receiveNewMaterial(unit, type, quantity, unitName, attributeValues, batchProcess, note, SOURCE_ADJUSTMENT,
			TYPE_ADJUSTMENT_IN);
	}

	/** Sayim duzeltmesiyle dusulen miktar zayi (discarded). */
	@Transactional
	Row adjustmentDecrease(Row unit, Row material, double quantity, Row batchProcess, String note) {
		assertPositiveQuantity(quantity);
		lock(unit.id());
		assertMaterialInsideStorage(unit, material);
		Row moving = extractPortion(material, quantity, batchProcess);
		db.update("materials", moving.id(), closed(moving, "discarded", batchProcess));
		Row movement = createMovement(unit.id(), moving, batchProcess, null, SOURCE_ADJUSTMENT, TYPE_ADJUSTMENT_OUT, null,
			quantity, note, false, null);
		recalculate(unit, moving);
		return movement;
	}

	/**
	 * Acilis, yeni materyal girisi ve duzeltme artisi: materyal konumsuz yaratilir, attribute degerleri
	 * yazilir, tank kurallari dogrulanir, sonra tanka yerlestirilir (hata olursa hicbir kayit kalmaz).
	 */
	private Row receiveNewMaterial(Row unit, Row type, double quantity, String unitName, List<Map<String, Object>> attributeValues,
		Row batchProcess, String note, String sourceType, String movementType) {
		assertPositiveQuantity(quantity);
		if (!Objects.equals(type.get("unit"), unitName)) {
			throw new BusinessRuleException("Ürün birimi ile işlem birimi uyuşmuyor.");
		}
		lock(unit.id());
		unit = reload(unit);
		assertProductionProductAllowed(unit, type);
		assertProductionProductTypeAllowed(unit, type);
		assertCapacityAvailable(unit, quantity, unitName);

		Map<String, Object> values = new LinkedHashMap<>();
		values.put("production_batch_id", batchProcess == null ? null : batchProcess.get("production_batch_id"));
		values.put("production_product_type_id", type.id());
		values.put("parent_material_id", null);
		values.put("origin_type", sourceType);
		values.put("quantity", quantity);
		values.put("unit", unitName);
		values.put("status", "active");
		values.put("current_location_type", null);
		values.put("current_location_id", null);
		values.put("source_production_stage_id", batchProcess == null ? null : batchProcess.get("production_stage_id"));
		values.put("created_by_batch_process_id", batchProcess == null ? null : batchProcess.id());
		values.put("closed_by_batch_process_id", null);
		values.put("note", note);
		Row material = db.create("materials", values);
		applyAttributeValues(material.id(), type, attributeValues);

		// Uyum kontrolu icin materyal veritabanindan (attribute degerleriyle); miktar verilen float
		assertCompatibleWithTank(unit, db.find("materials", material.id()).orElseThrow(), quantity);
		db.update("materials", material.id(), location("storage_unit", unit.id()));
		Row movement = createMovement(unit.id(), material, batchProcess, null, sourceType, movementType, null, quantity, note, false, null);
		recalculate(unit, db.find("materials", material.id()).orElseThrow());
		return movement;
	}

	// ======================================================================
	// Kurallar
	// ======================================================================

	private static void assertPositiveQuantity(double quantity) {
		if (quantity <= 0) {
			throw new BusinessRuleException("Miktar sıfırdan büyük olmalıdır.");
		}
	}

	private void assertTypeIsBatchOutput(Row batchProcess, Row type) {
		List<Row> outputs = db.whereIn("production_batch_output_production_product_types", "production_batch_id",
			List.of(Php.intval(batchProcess.get("production_batch_id"))), "sort_order asc");
		boolean isOutput = outputs.stream().anyMatch(o -> Php.intval(o.get("production_product_type_id")) == type.id());
		if (!isOutput) {
			Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
				Relations.column(outputs, "production_product_type_id"), null));
			List<String> names = new ArrayList<>();
			for (Row o : outputs) {
				Row t = types.get(Relations.key(o.get("production_product_type_id")));
				String name = t == null ? null : Php.strval(t.get("name"));
				if (name != null && !name.isEmpty() && !name.equals("0")) {
					names.add(name);
				}
			}
			Row batch = db.find("production_batches", Php.intval(batchProcess.get("production_batch_id"))).orElse(null);
			throw new BusinessRuleException("'" + Php.strval(type.get("name")) + "', '" + Php.strval(batch == null ? null : batch.get("batch_code"))
				+ "' partisinin çıktılarından biri değil. Partinin çıktıları: " + String.join(", ", names) + ".");
		}
	}

	private void assertProductionProductAllowed(Row unit, Row type) {
		if (db.count("select count(*) as aggregate from storage_unit_allowed_production_products"
			+ " where storage_unit_allowed_production_products.storage_unit_id = ?"
			+ " and storage_unit_allowed_production_products.storage_unit_id is not null", unit.id()) == 0) {
			return;
		}
		if (!exists("storage_unit_allowed_production_products", unit.id(), "production_product_id", type.get("production_product_id"))) {
			throw new BusinessRuleException("Seçilen tank bu ürün tipini kabul etmiyor.");
		}
	}

	private void assertProductionProductTypeAllowed(Row unit, Row type) {
		if (db.count("select count(*) as aggregate from storage_unit_allowed_production_product_types"
			+ " where storage_unit_allowed_production_product_types.storage_unit_id = ?"
			+ " and storage_unit_allowed_production_product_types.storage_unit_id is not null", unit.id()) == 0) {
			return;
		}
		if (!exists("storage_unit_allowed_production_product_types", unit.id(), "production_product_type_id", type.id())) {
			throw new BusinessRuleException("Seçilen tank bu spesifik ürünü kabul etmiyor.");
		}
	}

	private boolean exists(String table, long unitId, String column, Object value) {
		if (value == null) {
			return db.count("select count(*) from " + table + " where storage_unit_id = ? and " + column + " is null", unitId) > 0;
		}
		return db.count("select count(*) from " + table + " where storage_unit_id = ? and " + column + " = ?", unitId, value) > 0;
	}

	private static void assertMaterialInsideStorage(Row unit, Row material) {
		if (!"storage_unit".equals(material.get("current_location_type")) || Php.intval(material.get("current_location_id")) != unit.id()) {
			throw new BusinessRuleException("Seçilen material bu tankın içinde bulunmuyor.");
		}
		if (!"active".equals(material.get("status"))) {
			throw new BusinessRuleException("Sadece aktif material üzerinde işlem yapılabilir.");
		}
	}

	/** StorageBalanceService::assertCapacityAvailable */
	private void assertCapacityAvailable(Row unit, double incoming, String unitName) {
		if (!Objects.equals(unit.get("capacity_unit"), unitName)) {
			throw new BusinessRuleException("Tank kapasite birimi ile işlem birimi uyuşmuyor.");
		}
		Row sum = db.first("select sum(current_quantity) as aggregate from storage_unit_balances where storage_unit_id = ? and unit = ?",
			unit.id(), unitName).orElseThrow();
		double current = Php.floatval(sum.get("aggregate"));
		double capacity = Php.floatval(Casts.decimal(unit.get("capacity"), 2));
		if (current + incoming > capacity) {
			throw new BusinessRuleException("Hedef tankta yeterli boş kapasite yok.");
		}
	}

	/**
	 * Tank bossa yalnizca attribute kurallari; doluysa once farkli urun (kategori) ve farkli tip kurallari.
	 * @param incomingQuantity tanka fiilen girecek miktar (agirlikli ortalamada agirlik)
	 */
	private void assertCompatibleWithTank(Row unit, Row incoming, double incomingQuantity) {
		List<Row> existing = db.select("select * from materials where current_location_type = ? and current_location_id = ?"
			+ " and status = ?", "storage_unit", unit.id(), "active");
		List<Row> all = new ArrayList<>(existing);
		all.add(incoming);
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(all, "production_product_type_id"), null));
		Map<Long, List<Row>> values = Relations.groupBy(db.whereIn("material_attribute_values", "material_id",
			Relations.ids(all), null), "material_id");
		Row incomingType = types.get(Relations.key(incoming.get("production_product_type_id")));
		if (!existing.isEmpty()) {
			if (!Boolean.TRUE.equals(Casts.bool(unit.get("allow_multiple_production_products")))
				&& existing.stream().anyMatch(m -> productOf(types, m) != Php.intval(incomingType.get("production_product_id")))) {
				throw new BusinessRuleException("Bu tank doluyken farklı ürün tipleri bir arada tutulamaz.");
			}
			if (!Boolean.TRUE.equals(Casts.bool(unit.get("allow_multiple_production_product_types")))
				&& existing.stream().anyMatch(m -> Php.intval(m.get("production_product_type_id")) != Php.intval(incoming.get("production_product_type_id")))) {
				throw new BusinessRuleException("Bu tank doluyken farklı ürünler bir arada tutulamaz.");
			}
		}
		assertAttributeRules(unit, incoming, incomingType, existing, types, values, incomingQuantity);
	}

	private void assertAttributeRules(Row unit, Row incoming, Row incomingType, List<Row> existing, Map<Long, Row> types,
		Map<Long, List<Row>> values, double incomingQuantity) {
		List<Row> rules = db.whereIn("storage_unit_attribute_rules", "storage_unit_id", List.of(unit.id()), null).stream()
			.filter(r -> Boolean.TRUE.equals(Casts.bool(r.get("is_active")))).toList();
		if (rules.isEmpty()) {
			return;
		}
		Map<Long, Row> mappings = Relations.byId(db.whereIn("production_product_attribute_definitions", "id",
			Relations.column(rules, "production_product_attribute_definition_id"), null));
		Map<Long, Row> attributes = Relations.byId(db.whereIn("attribute_definitions", "id",
			Relations.column(mappings.values(), "attribute_definition_id"), null));
		for (Row rule : rules) {
			Row definition = mappings.get(Relations.key(rule.get("production_product_attribute_definition_id")));
			if (definition == null) {
				continue;
			}
			long productId = Php.intval(definition.get("production_product_id"));
			if (productId != Php.intval(incomingType.get("production_product_id"))) {
				continue;
			}
			Row attribute = attributes.get(Relations.key(definition.get("attribute_definition_id")));
			if (attribute == null || !"decimal".equals(attribute.get("data_type"))) {
				throw new BusinessRuleException("Tank attribute rule şu anda sadece decimal attribute tipleri ile çalışır.");
			}
			Double incomingValue = decimalValue(values.get(incoming.id()), definition.id());
			if (incomingValue == null) {
				throw new BusinessRuleException("Gelen material üzerinde tank kuralı için gerekli attribute değeri bulunamadı.");
			}
			Double min = rule.get("min_value_decimal") == null ? null : Php.floatval(Casts.decimal(rule.get("min_value_decimal"), 4));
			Double max = rule.get("max_value_decimal") == null ? null : Php.floatval(Casts.decimal(rule.get("max_value_decimal"), 4));
			String label = attribute.get("label") != null ? Php.strval(attribute.get("label")) : Php.strval(attribute.get("attribute_key"));
			String mode = Php.strval(rule.get("apply_mode"));
			if (mode.equals("incoming_only") || mode.equals("incoming_and_average")) {
				assertWithinRange(incomingValue, min, max, label);
			}
			if (mode.equals("tank_average_only") || mode.equals("incoming_and_average")) {
				double weighted = 0.0;
				double total = 0.0;
				for (Row m : existing) {
					if (productOf(types, m) != productId) {
						continue;
					}
					Double value = decimalValue(values.get(m.id()), definition.id());
					if (value == null) {
						throw new BusinessRuleException("Tank içindeki mevcut material üzerinde gerekli attribute değeri bulunamadı.");
					}
					double q = Php.floatval(Casts.decimal(m.get("quantity"), 4));
					weighted += value * q;
					total += q;
				}
				weighted += incomingValue * incomingQuantity;
				total += incomingQuantity;
				if (total <= 0) {
					throw new BusinessRuleException("Tank ortalaması hesaplanamadı.");
				}
				assertWithinRange(weighted / total, min, max, label + " ortalaması");
			}
		}
	}

	/** Ilk (tanima ait, decimal degeri dolu) attribute degeri. */
	private static Double decimalValue(List<Row> values, long definitionId) {
		if (values == null) {
			return null;
		}
		for (Row v : values) {
			if (Php.intval(v.get("production_product_attribute_definition_id")) == definitionId && v.get("value_decimal") != null) {
				return Php.floatval(Casts.decimal(v.get("value_decimal"), 4));
			}
		}
		return null;
	}

	private static void assertWithinRange(double value, Double min, Double max, String label) {
		if (min != null && value < min) {
			throw new BusinessRuleException(label + " değeri tank için izin verilen minimum sınırın altında.");
		}
		if (max != null && value > max) {
			throw new BusinessRuleException(label + " değeri tank için izin verilen maksimum sınırın üstünde.");
		}
	}

	// ======================================================================
	// Materyal ve bakiye
	// ======================================================================

	/**
	 * Tamami (epsilon toleransiyla) isteniyorsa materyalin kendisi; degilse ayni ozelliklerde bir cocuk
	 * materyal (attribute degerleri kopyalanir) ve ebeveynin miktari dusulur.
	 */
	private Row extractPortion(Row material, double quantity, Row batchProcess) {
		double current = Php.floatval(Casts.decimal(material.get("quantity"), 4));
		if (quantity - current > QUANTITY_EPSILON) {
			throw new BusinessRuleException("Material üzerinde yeterli miktar bulunmuyor.");
		}
		List<Row> attributeValues = db.whereIn("material_attribute_values", "material_id", List.of(material.id()), null);
		if (Math.abs(current - quantity) <= QUANTITY_EPSILON) {
			return material;
		}
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("production_batch_id", material.get("production_batch_id"));
		values.put("production_product_type_id", material.get("production_product_type_id"));
		values.put("parent_material_id", material.id());
		values.put("origin_type", material.get("origin_type"));
		values.put("quantity", quantity);
		values.put("unit", material.get("unit"));
		values.put("status", "active");
		values.put("current_location_type", material.get("current_location_type"));
		values.put("current_location_id", material.get("current_location_id"));
		values.put("source_production_stage_id", material.get("source_production_stage_id"));
		values.put("created_by_batch_process_id", batchProcess != null ? batchProcess.id() : material.get("created_by_batch_process_id"));
		values.put("closed_by_batch_process_id", null);
		values.put("note", material.get("note"));
		long childId = db.insert("materials", values);
		for (Row v : attributeValues) {
			Map<String, Object> copy = new LinkedHashMap<>();
			copy.put("production_product_attribute_definition_id", v.get("production_product_attribute_definition_id"));
			copy.put("value_boolean", Casts.bool(v.get("value_boolean")));
			copy.put("value_integer", Casts.integer(v.get("value_integer")));
			copy.put("value_decimal", Casts.decimal(v.get("value_decimal"), 4));
			copy.put("value_text", v.get("value_text"));
			copy.put("value_json", Db.json(v.get("value_json")));
			copy.put("material_id", childId);
			db.insert("material_attribute_values", copy);
		}
		db.update("materials", material.id(), Map.of("quantity", current - quantity));
		return db.find("materials", childId).orElseThrow();
	}

	/** Gecersiz tanim (kategoride yok) 422; gecerliler sirayla yazilir. */
	private void applyAttributeValues(long materialId, Row type, List<Map<String, Object>> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		Map<Long, Row> definitions = Relations.byId(db.select("select * from production_product_attribute_definitions"
			+ " where production_product_id = ?", type.get("production_product_id")));
		for (Map<String, Object> item : items) {
			Object definitionId = item.get("production_product_attribute_definition_id");
			if (!truthy(definitionId) || !definitions.containsKey(Php.intval(definitionId))) {
				throw LaravelValidationException.of("attribute_values", "Geçersiz attribute definition gönderildi.");
			}
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("production_product_attribute_definition_id", definitionId);
			values.put("value_boolean", item.get("value_boolean"));
			values.put("value_integer", item.get("value_integer"));
			values.put("value_decimal", item.get("value_decimal"));
			values.put("value_text", item.get("value_text"));
			values.put("value_json", Db.json(item.get("value_json")));
			values.put("material_id", materialId);
			db.insert("material_attribute_values", values);
		}
	}

	/**
	 * StorageBalanceService::recalculateForMaterial: tanktaki aktif materyallerin (ayni kategori, ayni birim)
	 * toplami; 0 ise bakiye silinir, degilse updateOrCreate ({@code decimal:2} ile degismediyse dokunulmaz).
	 */
	private void recalculate(Row unit, Row material) {
		Row type = typeOf(material);
		Object productId = type.get("production_product_id");
		String unitName = Php.strval(material.get("unit"));
		Row sum = db.first("select sum(quantity) as aggregate from materials where current_location_type = ?"
			+ " and current_location_id = ? and status = ? and unit = ? and exists (select * from production_product_types"
			+ " where materials.production_product_type_id = production_product_types.id and production_product_id = ?)",
			"storage_unit", unit.id(), "active", unitName, productId).orElseThrow();
		double total = Php.floatval(sum.get("aggregate"));
		if (total <= 0) {
			db.execute("delete from storage_unit_balances where storage_unit_id = ? and production_product_id = ? and unit = ?",
				unit.id(), productId, unitName);
			return;
		}
		Row balance = db.first("select * from storage_unit_balances where storage_unit_id = ? and production_product_id = ?"
			+ " and unit = ? limit 1", unit.id(), productId, unitName).orElse(null);
		if (balance == null) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("storage_unit_id", unit.id());
			values.put("production_product_id", productId);
			values.put("unit", unitName);
			values.put("current_quantity", total);
			db.insert("storage_unit_balances", values);
		}
		else if (!Objects.equals(Casts.decimal(balance.get("current_quantity"), 2), Casts.decimal(Php.floatString(total), 2))) {
			db.update("storage_unit_balances", balance.id(), Map.of("current_quantity", total));
		}
	}

	private Row createMovement(long unitId, Row material, Row batchProcess, Long counterpartyId, String sourceType,
		String movementType, String reference, double quantity, String note, boolean withRun, Long runId) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("storage_unit_id", unitId);
		values.put("material_id", material.id());
		values.put("batch_process_id", batchProcess == null ? null : batchProcess.id());
		if (withRun) {
			values.put("finished_product_run_id", runId);
		}
		values.put("counterparty_storage_unit_id", counterpartyId);
		values.put("source_type", sourceType);
		values.put("movement_type", movementType);
		values.put("transfer_reference", reference);
		values.put("quantity", quantity);
		values.put("unit", material.get("unit"));
		values.put("note", note);
		values.put("moved_at", Timestamps.now());
		return db.create("storage_movements", values);
	}

	// ======================================================================
	// yardimcilar
	// ======================================================================

	/** Islemdeki tanklar (id sirasiyla, kilitlenme olmasin). */
	private void lock(long... unitIds) {
		List<Long> ids = java.util.Arrays.stream(unitIds).sorted().boxed().toList();
		db.select("select id from storage_units where id in (" + Relations.inList(ids) + ") order by id for update");
	}

	private Row reload(Row unit) {
		return db.find("storage_units", unit.id()).orElseThrow();
	}

	private Row typeOf(Row material) {
		return db.find("production_product_types", Php.intval(material.get("production_product_type_id"))).orElseThrow();
	}

	private static long productOf(Map<Long, Row> types, Row material) {
		Row type = types.get(Relations.key(material.get("production_product_type_id")));
		return type == null ? 0 : Php.intval(type.get("production_product_id"));
	}

	private static Map<String, Object> location(String type, Long id) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("current_location_type", type);
		values.put("current_location_id", id);
		return values;
	}

	private static Map<String, Object> closed(Row material, String status, Row batchProcess) {
		Map<String, Object> values = location(null, null);
		values.put("status", status);
		values.put("closed_by_batch_process_id", batchProcess != null ? batchProcess.id() : material.get("closed_by_batch_process_id"));
		return values;
	}

	/** PHP truthiness ({@code !$value}): null, 0, "0", "" ve bos dizi false. */
	private static boolean truthy(Object value) {
		return switch (value) {
			case null -> false;
			case Boolean b -> b;
			case String s -> !s.isEmpty() && !s.equals("0");
			case Number n -> new java.math.BigDecimal(n.toString()).signum() != 0;
			case Collection<?> c -> !c.isEmpty();
			case Map<?, ?> m -> !m.isEmpty();
			default -> true;
		};
	}

	/** {@code Str::random}: harf ve rakam. */
	private static String random(int length) {
		StringBuilder out = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			out.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
		}
		return out.toString();
	}


	// ---- hareket gecmisi ----------------------------------------------------

	/** Tankin hareketleri: {@code dateFrom}/{@code dateTo} moved_at::date araligi ("0" verilmemis sayilir). */
	record History(String where, List<Object> args) {
	}

	History history(long unitId, String from, String to) {
		StringBuilder where = new StringBuilder(" from storage_movements where storage_unit_id = ?");
		List<Object> args = new ArrayList<>(List.of(unitId));
		// when($dateFrom): PHP'de "0" de false
		if (from != null && !from.equals("0")) {
			where.append(" and moved_at::date >= ?");
			args.add(from);
		}
		if (to != null && !to.equals("0")) {
			where.append(" and moved_at::date <= ?");
			args.add(to);
		}
		return new History(where.toString(), args);
	}

	/** En yeni once (moved_at desc, id desc); {@code perPage} null ise hepsi. */
	List<Row> history(History h, Integer page, Integer perPage) {
		String sql = "select *" + h.where() + " order by moved_at desc, id desc";
		if (perPage != null) {
			sql += " limit " + perPage + " offset " + (long) (page - 1) * perPage;
		}
		return db.select(sql, h.args().toArray());
	}

	long historyCount(History h) {
		return db.count("select count(*) as aggregate" + h.where(), h.args().toArray());
	}

}
