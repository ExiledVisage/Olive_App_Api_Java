package com.olivamore.api.production;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Asama tamamlanirken calisan uzanti isleyicileri, Laravel'deki servislerle ayni sirada ve ayni mesajlarla:
 * <ul>
 *   <li>{@code weighing} ({@code WeighingBatchProcessService}): partideki urunun miktari; materyal yoksa yaratilir,
 *       varsa miktari dusurulur (artirilamaz)</li>
 *   <li>{@code material_transformation}: girdi materyali (yoksa urun tipinden yaratilir) tuketilir, ciktilar
 *       yaratilir; ayni birimdeki ciktilar girdiyi asamaz</li>
 *   <li>{@code analysis}: materyalin attribute degerleri (updateOrCreate)</li>
 *   <li>{@code storage_movement}: tanka giris, cikis ya da transfer (asamada sabitlenmis tur)</li>
 * </ul>
 * {@code process} bellekteki proses: kaydedilmemis {@code note} degisikligi dahil (Laravel'de model).
 */
@Component
class StageHandlers {

	/** materials.quantity decimal(14,4) */
	static final double QUANTITY_EPSILON = 0.00005;

	private final Db db;
	private final StockOperations stock;

	StageHandlers(Db db, StockOperations stock) {
		this.db = db;
		this.stock = stock;
	}

	/** Asamanin uzantisi: tip anahtari, girdi kategorileri, cikti kategorileri (urun anahtariyla), degerler. */
	record Extension(Row row, String key, boolean enabled, List<Long> inputProductIds, List<Row> outputProducts, List<Row> values) {
	}

	/** {@code productionStage.extensions} (sort_order sirasinda) ve iliskileri. */
	List<Extension> extensions(long stageId) {
		List<Row> rows = db.whereIn("production_stage_extensions", "production_stage_id", List.of(stageId), "sort_order asc");
		List<Long> ids = Relations.ids(rows);
		Map<Long, Row> types = Relations.byId(db.whereIn("stage_extension_types", "id",
			Relations.column(rows, "stage_extension_type_id"), null));
		Map<Long, List<Row>> inputs = Relations.groupBy(db.whereIn("production_stage_extension_input_production_products",
			"production_stage_extension_id", ids, "sort_order asc"), "production_stage_extension_id");
		List<Row> outputRows = db.whereIn("production_stage_extension_output_production_products", "production_stage_extension_id",
			ids, "sort_order asc");
		Map<Long, Row> products = Relations.byId(db.whereIn("production_products", "id",
			Relations.column(outputRows, "production_product_id"), null));
		Map<Long, List<Row>> outputs = new LinkedHashMap<>();
		for (Row o : outputRows) {
			Map<String, Object> values = new LinkedHashMap<>(o.values());
			Row product = products.get(Relations.key(o.get("production_product_id")));
			values.put("product_key", product == null ? null : product.get("key"));
			outputs.computeIfAbsent(o.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(new Row(values));
		}
		List<Row> valueRows = db.whereIn("production_stage_extension_values", "production_stage_extension_id", ids, null);
		Map<Long, Row> fields = Relations.byId(db.whereIn("stage_extension_type_fields", "id",
			Relations.column(valueRows, "stage_extension_type_field_id"), null));
		Map<Long, List<Row>> values = new LinkedHashMap<>();
		for (Row v : valueRows) {
			Map<String, Object> copy = new LinkedHashMap<>(v.values());
			Row field = fields.get(Relations.key(v.get("stage_extension_type_field_id")));
			copy.put("field_key", field == null ? null : field.get("field_key"));
			values.computeIfAbsent(v.longValue("production_stage_extension_id"), k -> new ArrayList<>()).add(new Row(copy));
		}
		List<Extension> out = new ArrayList<>();
		for (Row e : rows) {
			Row type = types.get(Relations.key(e.get("stage_extension_type_id")));
			out.add(new Extension(e, type == null ? null : Php.strval(type.get("key")), Boolean.TRUE.equals(Casts.bool(e.get("is_enabled"))),
				Relations.column(inputs.getOrDefault(e.id(), List.of()), "production_product_id"),
				outputs.getOrDefault(e.id(), List.of()), values.getOrDefault(e.id(), List.of())));
		}
		return out;
	}

	/** {@code resolveExtensionKeys}: etkin uzantilarin anahtarlari, sort_order sirasinda. */
	static List<String> keys(List<Extension> extensions) {
		return extensions.stream().filter(Extension::enabled).map(Extension::key).filter(Objects::nonNull).toList();
	}

	/** {@code pinnedAction}: depo hareketi uzantisinda sabitlenmis tur (bos metin sabitlenmemis sayilir). */
	static String pinnedAction(List<Extension> extensions) {
		Extension extension = first(extensions, "storage_movement");
		if (extension == null) {
			return null;
		}
		Row value = extension.values().stream().filter(v -> "action".equals(v.get("field_key"))).findFirst().orElse(null);
		String action = value == null || value.get("value_text") == null ? null : Php.strval(value.get("value_text")).strip();
		return action == null || action.isEmpty() ? null : action;
	}

	void dispatch(Row process, List<Extension> extensions, Map<String, Object> data) {
		for (String key : keys(extensions)) {
			switch (key) {
				case "weighing" -> weighing(process, extensions, data);
				case "material_transformation" -> transformation(process, extensions, data);
				case "analysis" -> analysis(process, extensions, data);
				case "storage_movement" -> storageMovement(process, extensions, data);
				default -> {
				}
			}
		}
	}

	// ======================================================================
	// Tartim
	// ======================================================================

	private void weighing(Row process, List<Extension> extensions, Map<String, Object> data) {
		Extension extension = require(extensions, "weighing", "Weighing extension bulunamadı.");
		List<Row> candidates = candidateTypes(process, extension);
		Row type;
		if (truthy(data.get("production_product_type_id"))) {
			long id = Php.intval(data.get("production_product_type_id"));
			type = candidates.stream().filter(t -> t.id() == id).findFirst()
				.orElseThrow(() -> new BusinessRuleException("Seçilen ürün bu weighing aşaması için batch içinde uygun değil."));
		}
		else if (candidates.isEmpty()) {
			throw new BusinessRuleException("Bu weighing aşaması için batch içinde uygun ürün bulunamadı.");
		}
		else if (candidates.size() > 1) {
			throw new BusinessRuleException(
				"Bu weighing aşaması için birden fazla uygun ürün bulundu. production_product_type_id gönderilmelidir.");
		}
		else {
			type = candidates.getFirst();
		}
		double quantity = data.get("quantity") != null ? Php.floatval(data.get("quantity")) : 0;
		if (quantity <= 0) {
			throw new BusinessRuleException("Weighing işlemi için quantity zorunludur.");
		}
		Object unit = data.get("unit") != null ? data.get("unit") : type.get("unit");
		if (!Objects.equals(unit, type.get("unit"))) {
			throw new BusinessRuleException("Tartım birimi ürün birimi ile uyuşmuyor.");
		}
		Row material = db.first("select * from materials where production_batch_id = ? and production_product_type_id = ?"
			+ " and status = ? limit 1", process.get("production_batch_id"), type.id(), "active").orElse(null);
		if (material == null) {
			createMaterial(process, type.id(), null, quantity, unit);
			return;
		}
		double current = Php.floatval(Casts.decimal(material.get("quantity"), 4));
		if (quantity - current > QUANTITY_EPSILON) {
			String u = Php.strval(material.get("unit"));
			throw new BusinessRuleException("Tartım kayıtlı miktarı artıramaz: bu üründen " + Php.floatString(current) + " " + u
				+ " görünüyor, " + Php.floatString(quantity) + " " + u + " girildi. Üretim aşamaları "
				+ "ürünü azaltır, çoğaltmaz. Terazi daha fazlasını gösteriyorsa ürünün "
				+ "sisteme girildiği aşamadaki miktar yanlış demektir.");
		}
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("quantity", decimal4(quantity));
		values.put("unit", unit);
		values.put("source_production_stage_id", process.get("production_stage_id"));
		values.put("current_location_type", material.get("current_location_type") != null ? material.get("current_location_type") : "production_batch");
		values.put("current_location_id", material.get("current_location_id") != null ? material.get("current_location_id") : process.get("production_batch_id"));
		values.put("note", process.get("note") != null ? process.get("note") : material.get("note"));
		db.update("materials", material.id(), values);
	}

	// ======================================================================
	// Donusum
	// ======================================================================

	private void transformation(Row process, List<Extension> extensions, Map<String, Object> data) {
		Extension extension = require(extensions, "material_transformation", "Material transformation extension bulunamadı.");
		double inputQuantity = data.get("input_quantity") != null ? Php.floatval(data.get("input_quantity")) : 0;
		if (inputQuantity <= 0) {
			throw new BusinessRuleException("Transformation için input_quantity zorunludur.");
		}
		Row input = inputMaterial(process, extension, data, inputQuantity);
		Row inputType = db.find("production_product_types", Php.intval(input.get("production_product_type_id"))).orElseThrow();

		List<Map<String, Object>> outputs = items(data.get("outputs"));
		if (outputs.isEmpty()) {
			throw new BusinessRuleException("Transformation işlemi için en az bir output gerekir.");
		}
		double sameUnitTotal = 0.0;
		for (Map<String, Object> output : outputs) {
			Row type = outputType(process, inputType, extension, output);
			double quantity = output.get("quantity") != null ? Php.floatval(output.get("quantity")) : 0;
			if (quantity <= 0) {
				throw new BusinessRuleException("Output quantity sıfırdan büyük olmalıdır.");
			}
			Object unit = output.get("unit") != null ? output.get("unit") : type.get("unit");
			if (!Objects.equals(unit, type.get("unit"))) {
				throw new BusinessRuleException("Output unit ürün birimi ile uyuşmuyor.");
			}
			if (Objects.equals(unit, input.get("unit"))) {
				sameUnitTotal += quantity;
			}
			createMaterial(process, type.id(), input.id(), quantity, unit);
		}

		if (sameUnitTotal > 0.0 && sameUnitTotal - inputQuantity > QUANTITY_EPSILON) {
			String u = Php.strval(input.get("unit"));
			throw new BusinessRuleException("Kütle dengesi tutmuyor: girdi " + Php.floatString(inputQuantity) + " " + u + ", "
				+ "aynı birimdeki çıktı toplamı " + Php.floatString(sameUnitTotal) + " " + u + ". "
				+ "Çıktı girdiden fazla olamaz.");
		}

		// applyInputConsumption
		double current = Php.floatval(Casts.decimal(input.get("quantity"), 4));
		if (inputQuantity > current) {
			throw new BusinessRuleException("Input quantity, seçilen material miktarından büyük olamaz.");
		}
		double remaining = current - inputQuantity;
		Object note = process.get("note") != null ? process.get("note") : input.get("note");
		Map<String, Object> values = new LinkedHashMap<>();
		if (remaining <= 0.000001) {
			values.put("status", "consumed");
			values.put("closed_by_batch_process_id", process.id());
		}
		else {
			values.put("quantity", decimal4(remaining));
		}
		values.put("note", note);
		db.update("materials", input.id(), values);
	}

	/** Partide uygun aktif materyal varsa o (secilen ya da tek), yoksa urun tipinden yeni girdi materyali. */
	private Row inputMaterial(Row process, Extension extension, Map<String, Object> data, double inputQuantity) {
		List<Row> candidates = candidateMaterials(process, extension.inputProductIds());
		if (!candidates.isEmpty()) {
			Row material;
			if (truthy(data.get("input_material_id"))) {
				long id = Php.intval(data.get("input_material_id"));
				material = candidates.stream().filter(m -> m.id() == id).findFirst()
					.orElseThrow(() -> new BusinessRuleException("Seçilen input material bu aşama için uygun değil."));
			}
			else if (candidates.size() > 1) {
				throw new BusinessRuleException("Birden fazla uygun input material bulundu. input_material_id gönderilmelidir.");
			}
			else {
				material = candidates.getFirst();
			}
			if (inputQuantity > Php.floatval(Casts.decimal(material.get("quantity"), 4))) {
				throw new BusinessRuleException("Input quantity, seçilen material miktarından büyük olamaz.");
			}
			return material;
		}
		List<Row> types = candidateTypes(process, extension);
		Row type;
		if (truthy(data.get("input_production_product_type_id"))) {
			long id = Php.intval(data.get("input_production_product_type_id"));
			type = types.stream().filter(t -> t.id() == id).findFirst()
				.orElseThrow(() -> new BusinessRuleException("Seçilen giriş ürün tipi bu aşama için uygun değil."));
		}
		else if (types.isEmpty()) {
			throw new BusinessRuleException("Transformation için uygun giriş ürün tipi bulunamadı.");
		}
		else if (types.size() > 1) {
			throw new BusinessRuleException(
				"Birden fazla uygun giriş ürün tipi bulundu. input_production_product_type_id gönderilmelidir.");
		}
		else {
			type = types.getFirst();
		}
		long id = createMaterial(process, type.id(), null, inputQuantity, type.get("unit"));
		return db.find("materials", id).orElseThrow();
	}

	private Row outputType(Row process, Row inputType, Extension extension, Map<String, Object> output) {
		List<Row> batchOutputs = batchTypes("production_batch_output_production_product_types", process);
		if (truthy(output.get("production_product_type_id"))) {
			long id = Php.intval(output.get("production_product_type_id"));
			List<Long> allowed = Relations.column(extension.outputProducts(), "production_product_id");
			return batchOutputs.stream()
				.filter(t -> t.id() == id)
				.filter(t -> allowed.contains(Php.intval(t.get("production_product_id"))))
				.filter(t -> Objects.equals(t.get("type"), inputType.get("type")) || t.get("type") == null)
				.findFirst()
				.orElseThrow(() -> new BusinessRuleException("Seçilen çıkış ürün tipi bu aşama için uygun değil."));
		}
		long productId;
		if (truthy(output.get("production_product_id"))) {
			productId = Php.intval(output.get("production_product_id"));
		}
		else {
			Row match = !truthy(output.get("product_type_key")) ? null : extension.outputProducts().stream()
				.filter(o -> o.get("product_key") != null && o.get("product_key").equals(output.get("product_type_key")))
				.findFirst().orElse(null);
			if (match == null) {
				throw new BusinessRuleException("Çıkış ürün tipinin type değeri çözümlenemedi.");
			}
			productId = Php.intval(match.get("production_product_id"));
		}
		List<Row> sameProduct = unique(batchOutputs.stream().filter(t -> Php.intval(t.get("production_product_id")) == productId).toList());
		// where('type', $inputType): gevsek karsilastirma (null == "")
		List<Row> candidates = sameProduct.stream().filter(t -> looseEquals(t.get("type"), inputType.get("type"))).toList();
		if (candidates.isEmpty()) {
			candidates = sameProduct.stream().filter(t -> t.get("type") == null).toList();
		}
		if (candidates.isEmpty()) {
			throw new BusinessRuleException("Giriş ürünüyle uyumlu çıkış ürün tipi bulunamadı.");
		}
		if (candidates.size() > 1) {
			throw new BusinessRuleException("Birden fazla uygun çıkış ürün tipi bulundu. production_product_type_id gönderilmelidir.");
		}
		return candidates.getFirst();
	}

	// ======================================================================
	// Analiz
	// ======================================================================

	private void analysis(Row process, List<Extension> extensions, Map<String, Object> data) {
		Extension extension = require(extensions, "analysis", "Analysis extension bulunamadı.");
		Row material;
		if (truthy(data.get("material_id"))) {
			material = findOrFail("materials", "Material", data.get("material_id"));
			if (Php.intval(material.get("production_batch_id")) != Php.intval(process.get("production_batch_id"))) {
				throw new BusinessRuleException("Seçilen material bu batch’e ait değil.");
			}
		}
		else {
			List<Row> materials = candidateMaterials(process, extension.inputProductIds());
			if (materials.isEmpty()) {
				throw new BusinessRuleException("Analysis için uygun material bulunamadı.");
			}
			if (materials.size() > 1) {
				throw new BusinessRuleException("Birden fazla uygun material bulundu. material_id gönderilmelidir.");
			}
			material = materials.getFirst();
		}
		List<Map<String, Object>> attributes = items(data.get("attributes"));
		if (attributes.isEmpty()) {
			throw new BusinessRuleException("Analysis işlemi için attributes zorunludur.");
		}
		Object productId = db.find("production_product_types", Php.intval(material.get("production_product_type_id"))).orElseThrow()
			.get("production_product_id");
		for (Map<String, Object> attribute : attributes) {
			Row definition = definition(productId, attribute);
			Map<String, Object> payload = attributePayload(definition, attribute);
			Row existing = db.first("select * from material_attribute_values where material_attribute_values.material_id = ?"
				+ " and material_attribute_values.material_id is not null and production_product_attribute_definition_id = ? limit 1",
				material.id(), definition.id()).orElse(null);
			if (existing == null) {
				Map<String, Object> values = new LinkedHashMap<>();
				values.put("production_product_attribute_definition_id", definition.id());
				values.putAll(payload);
				values.put("material_id", material.id());
				db.insert("material_attribute_values", values);
			}
			else {
				db.update("material_attribute_values", existing.id(), payload);
			}
		}
	}

	private Row definition(Object productId, Map<String, Object> attribute) {
		if (truthy(attribute.get("production_product_attribute_definition_id"))) {
			Object id = attribute.get("production_product_attribute_definition_id");
			return db.first("select * from production_product_attribute_definitions where production_product_id = ?"
				+ " and production_product_attribute_definitions.id = ? limit 1", productId, id)
				.orElseThrow(() -> new ModelNotFoundException("ProductionProductAttributeDefinition", id));
		}
		if (truthy(attribute.get("attribute_key"))) {
			Row found = db.first("select * from production_product_attribute_definitions where production_product_id = ? and exists"
				+ " (select * from attribute_definitions where production_product_attribute_definitions.attribute_definition_id"
				+ " = attribute_definitions.id and attribute_key = ?) limit 1", productId, attribute.get("attribute_key")).orElse(null);
			if (found != null) {
				return found;
			}
		}
		throw new BusinessRuleException("Attribute definition çözümlenemedi.");
	}

	/** Tek {@code value} verilip value_* verilmediyse tanimin veri tipine gore; degilse value_* oldugu gibi. */
	private Map<String, Object> attributePayload(Row definition, Map<String, Object> attribute) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("value_boolean", null);
		payload.put("value_integer", null);
		payload.put("value_decimal", null);
		payload.put("value_text", null);
		payload.put("value_json", Db.json(null));
		boolean typed = List.of("value_boolean", "value_integer", "value_decimal", "value_text", "value_json").stream()
			.anyMatch(attribute::containsKey);
		if (!typed && attribute.containsKey("value")) {
			Object value = attribute.get("value");
			Row attributeDefinition = db.find("attribute_definitions", Php.intval(definition.get("attribute_definition_id"))).orElse(null);
			String dataType = attributeDefinition == null || attributeDefinition.get("data_type") == null ? null
				: Php.strval(attributeDefinition.get("data_type"));
			switch (dataType == null ? "" : dataType) {
				case "boolean" -> payload.put("value_boolean", truthy(value));
				case "integer" -> payload.put("value_integer", Php.intval(value));
				case "decimal" -> payload.put("value_decimal", decimal4(Php.floatval(value)));
				case "string", "text" -> payload.put("value_text", Php.strval(value));
				case "json" -> payload.put("value_json", Db.json(BatchProcessFlow.phpJson(value)));
				default -> throw new BusinessRuleException("Desteklenmeyen attribute data_type değeri: '" + (dataType == null ? "" : dataType) + "'.");
			}
			return payload;
		}
		payload.put("value_boolean", attribute.get("value_boolean"));
		payload.put("value_integer", attribute.get("value_integer"));
		payload.put("value_decimal", attribute.get("value_decimal"));
		payload.put("value_text", attribute.get("value_text"));
		payload.put("value_json", Db.json(BatchProcessFlow.phpJson(attribute.get("value_json"))));
		return payload;
	}

	// ======================================================================
	// Depo hareketi
	// ======================================================================

	private void storageMovement(Row process, List<Extension> extensions, Map<String, Object> data) {
		String pinned = pinnedAction(extensions);
		Object sent = data.get("action");
		String action;
		if (pinned == null) {
			action = sent == null ? "inbound" : Php.strval(sent);
		}
		else {
			if (sent != null && !pinned.equals(sent)) {
				throw new BusinessRuleException("Bu aşamada hareket türü '" + pinned + "' olarak sabitlenmiş; '" + Php.strval(sent)
					+ "' gönderilemez.");
			}
			action = pinned;
		}
		Object note = data.get("note") != null ? data.get("note") : process.get("note");
		String noteText = note == null ? null : Php.strval(note);
		double quantity = data.get("quantity") != null ? Php.floatval(data.get("quantity")) : 0;
		switch (action) {
			case "inbound", "outbound" -> {
				Row material = storageMaterial(process, extensions, data);
				Object unitId = data.get("storage_unit_id");
				if (!truthy(unitId) || quantity <= 0) {
					throw new BusinessRuleException(("inbound".equals(action) ? "Inbound" : "Outbound")
						+ " için storage_unit_id ve quantity zorunludur.");
				}
				Row unit = findOrFail("storage_units", "StorageUnit", unitId);
				if ("inbound".equals(action)) {
					stock.inbound(unit, material, quantity, process, noteText);
				}
				else {
					stock.outbound(unit, material, quantity, process, noteText);
				}
			}
			case "transfer" -> {
				Row material = storageMaterial(process, extensions, data);
				Object fromId = data.get("from_storage_unit_id");
				Object toId = data.get("to_storage_unit_id");
				if (!truthy(fromId) || !truthy(toId) || quantity <= 0) {
					throw new BusinessRuleException("Transfer için from_storage_unit_id, to_storage_unit_id ve quantity zorunludur.");
				}
				Row from = findOrFail("storage_units", "StorageUnit", fromId);
				Row to = findOrFail("storage_units", "StorageUnit", toId);
				stock.transfer(from, to, material, quantity, process, noteText);
			}
			default -> throw new BusinessRuleException("Geçersiz storage movement action gönderildi.");
		}
	}

	/** Uzantinin girdi kategorilerindeki (tanimliysa) aktif parti materyali; secilen ya da tek. */
	private Row storageMaterial(Row process, List<Extension> extensions, Map<String, Object> data) {
		Extension extension = first(extensions, "storage_movement");
		List<Long> categories = extension == null ? List.of() : extension.inputProductIds();
		if (truthy(data.get("material_id"))) {
			Row material = findOrFail("materials", "Material", data.get("material_id"));
			if (Php.intval(material.get("production_batch_id")) != Php.intval(process.get("production_batch_id"))) {
				throw new BusinessRuleException("Seçilen material bu batch’e ait değil.");
			}
			Row type = db.find("production_product_types", Php.intval(material.get("production_product_type_id"))).orElseThrow();
			if (!categories.isEmpty() && !categories.contains(Php.intval(type.get("production_product_id")))) {
				throw new BusinessRuleException("Seçilen material (" + Php.strval(type.get("name")) + ") bu aşamanın girdi ürünlerinden değil.");
			}
			return material;
		}
		List<Row> materials = categories.isEmpty()
			? db.select("select * from materials where production_batch_id = ? and status = ?", process.get("production_batch_id"), "active")
			: candidateMaterials(process, categories);
		if (materials.isEmpty()) {
			throw new BusinessRuleException("Storage movement için aktif material bulunamadı.");
		}
		if (materials.size() > 1) {
			throw new BusinessRuleException("Birden fazla aktif material bulundu. material_id gönderilmelidir.");
		}
		return materials.getFirst();
	}

	// ======================================================================
	// yardimcilar
	// ======================================================================

	/** Partinin giris + cikis urun tipleri (sort_order), uzantinin girdi kategorilerinden olanlar, tekil. */
	private List<Row> candidateTypes(Row process, Extension extension) {
		List<Row> all = new ArrayList<>(batchTypes("production_batch_input_production_product_types", process));
		all.addAll(batchTypes("production_batch_output_production_product_types", process));
		return unique(all.stream().filter(t -> extension.inputProductIds().contains(Php.intval(t.get("production_product_id")))).toList());
	}

	private List<Row> batchTypes(String table, Row process) {
		List<Row> links = db.whereIn(table, "production_batch_id", List.of(Php.intval(process.get("production_batch_id"))), "sort_order asc");
		Map<Long, Row> types = Relations.byId(db.whereIn("production_product_types", "id",
			Relations.column(links, "production_product_type_id"), null));
		List<Row> out = new ArrayList<>();
		for (Row link : links) {
			Row type = types.get(Relations.key(link.get("production_product_type_id")));
			if (type != null) {
				out.add(type);
			}
		}
		return out;
	}

	/** Partinin aktif materyalleri, tipi verilen kategorilerden (kategori yoksa hic). */
	private List<Row> candidateMaterials(Row process, List<Long> productIds) {
		if (productIds.isEmpty()) {
			return List.of();
		}
		return db.select("select * from materials where production_batch_id = ? and status = ? and exists (select * from"
			+ " production_product_types where materials.production_product_type_id = production_product_types.id and"
			+ " production_product_id in (" + Relations.inList(productIds) + "))", process.get("production_batch_id"), "active");
	}

	private long createMaterial(Row process, long typeId, Long parentId, double quantity, Object unit) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("production_batch_id", process.get("production_batch_id"));
		values.put("production_product_type_id", typeId);
		values.put("parent_material_id", parentId);
		values.put("origin_type", "BATCH_PROCESS");
		values.put("quantity", quantity);
		values.put("unit", unit);
		values.put("status", "active");
		values.put("current_location_type", "production_batch");
		values.put("current_location_id", process.get("production_batch_id"));
		values.put("source_production_stage_id", process.get("production_stage_id"));
		values.put("created_by_batch_process_id", process.id());
		values.put("closed_by_batch_process_id", null);
		values.put("note", process.get("note"));
		return db.insert("materials", values);
	}

	private Row findOrFail(String table, String model, Object id) {
		return db.first("select * from " + table + " where " + table + ".id = ? limit 1", id)
			.orElseThrow(() -> new ModelNotFoundException(model, id));
	}

	private static Extension first(List<Extension> extensions, String key) {
		return extensions.stream().filter(e -> e.enabled() && key.equals(e.key())).findFirst().orElse(null);
	}

	private static Extension require(List<Extension> extensions, String key, String message) {
		Extension extension = first(extensions, key);
		if (extension == null) {
			throw new BusinessRuleException(message);
		}
		return extension;
	}

	private static List<Row> unique(List<Row> rows) {
		List<Row> out = new ArrayList<>();
		for (Row r : rows) {
			if (out.stream().noneMatch(o -> o.id() == r.id())) {
				out.add(r);
			}
		}
		return out;
	}

	/** {@code $data['x'] ?? []} uzerinde foreach: her eleman bir dizi (degilse bos). */
	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Object value) {
		List<Object> list = value instanceof List<?> l ? new ArrayList<>(l)
			: value instanceof Map<?, ?> m ? new ArrayList<>(m.values()) : List.of();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Object item : list) {
			out.add(BatchProcessFlow.asMap(item));
		}
		return out;
	}

	/** decimal:4 cast'inin yazdigi deger (PHP float metni, 4 basamak). */
	static String decimal4(double value) {
		return Casts.decimal(Php.floatString(value), 4);
	}

	/** PHP {@code ==} metin/null icin: null ile "" esit. */
	private static boolean looseEquals(Object a, Object b) {
		Object x = a == null ? "" : a;
		Object y = b == null ? "" : b;
		return Php.strval(x).equals(Php.strval(y));
	}

	/** PHP {@code !empty()} */
	static boolean truthy(Object value) {
		return switch (value) {
			case null -> false;
			case Boolean b -> b;
			case String s -> !s.isEmpty() && !s.equals("0");
			case Number n -> new java.math.BigDecimal(n.toString()).signum() != 0;
			case java.util.Collection<?> c -> !c.isEmpty();
			case Map<?, ?> m -> !m.isEmpty();
			default -> true;
		};
	}

}
