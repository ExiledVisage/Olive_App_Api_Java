package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.olivamore.api.production.batchprocess.stage.StageExtensions.Extension;
import org.springframework.stereotype.Service;

/** Laravel {@code MaterialTransformationBatchProcessService}: girdi materyali (yoksa urun tipinden yaratilir) tuketilir, ciktilar yaratilir; ayni birimdeki ciktilar girdiyi asamaz. */
@Service
class MaterialTransformationBatchProcessService extends StageBatchProcessService {

	MaterialTransformationBatchProcessService(Db db, StockOperations stock) {
		super(db, stock);
	}

	void transformation(Row process, List<Extension> extensions, Map<String, Object> data) {
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

}
