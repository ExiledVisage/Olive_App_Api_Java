package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.production.batchprocess.stage.StageQueries.OutputProduct;
import com.olivamore.api.production.batchprocess.stage.StageQueries.ProductType;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Girdi materyali (yoksa tipinden yaratilir) tuketilir, ciktilar yaratilir; ayni birimdeki ciktilar girdiyi asamaz.
 * Uzantida kalan cikti ({@code remainder_product}) tanimliysa o ciktinin hesaplanan degeri (girdi - ayni birimdeki
 * diger ciktilar) ve girilen degerin ondan sapma orani ciktinin verisine yazilir.
 */
@Service
class MaterialTransformationBatchProcessService extends StageBatchProcessService {

	MaterialTransformationBatchProcessService(StageQueries queries, StockOperations stock) {
		super(queries, stock);
	}

	void transformation(StageProcess process, List<Extension> extensions, OperationData data) {
		Extension extension = require(extensions, "material_transformation",
			"Material transformation extension bulunamadı.");
		BigDecimal inputQuantity = data.quantity("input_quantity");
		if (inputQuantity.signum() <= 0) {
			throw new BusinessRuleException("Transformation için input_quantity zorunludur.");
		}
		Material input = inputMaterial(process, extension, data, inputQuantity);
		ProductType inputType = queries.productType(input.productionProductTypeId()).orElseThrow();

		List<OperationData> outputs = data.items("outputs");
		if (outputs.isEmpty()) {
			throw new BusinessRuleException("Transformation işlemi için en az bir output gerekir.");
		}
		List<Output> resolved = new ArrayList<>();
		BigDecimal sameUnitTotal = BigDecimal.ZERO;
		for (OperationData output : outputs) {
			ProductType type = outputType(process, inputType, extension, output);
			BigDecimal quantity = output.quantity("quantity");
			if (quantity.signum() <= 0) {
				throw new BusinessRuleException("Output quantity sıfırdan büyük olmalıdır.");
			}
			String unit = output.text("unit") != null ? output.text("unit") : type.unit();
			if (!Objects.equals(unit, type.unit())) {
				throw new BusinessRuleException("Output unit ürün birimi ile uyuşmuyor.");
			}
			boolean sameUnit = Objects.equals(unit, input.unit());
			if (sameUnit) {
				sameUnitTotal = sameUnitTotal.add(quantity);
			}
			resolved.add(new Output(output, type, quantity, sameUnit));
			createMaterial(process, type.id(), input.id(), quantity, unit);
		}
		recordRemainder(extension, resolved, inputQuantity);

		if (sameUnitTotal.signum() > 0 && sameUnitTotal.subtract(inputQuantity).compareTo(QUANTITY_EPSILON) > 0) {
			throw new BusinessRuleException("Kütle dengesi tutmuyor: girdi " + plain(inputQuantity) + " " + input.unit() + ", "
				+ "aynı birimdeki çıktı toplamı " + plain(sameUnitTotal) + " " + input.unit() + ". "
				+ "Çıktı girdiden fazla olamaz.");
		}
		if (inputQuantity.compareTo(input.quantity()) > 0) {
			throw new BusinessRuleException("Input quantity, seçilen material miktarından büyük olamaz.");
		}
		stock.consumeInput(input.id(), inputQuantity, process.id(), process.note());
	}

	private record Output(OperationData data, ProductType type, BigDecimal quantity, boolean sameUnit) {
	}

	/**
	 * Kalan cikti: hesaplanan = girdi - ayni birimdeki diger ciktilar; sapma = (girilen - hesaplanan) / hesaplanan.
	 * Girdiyle ayni birimde degilse ya da hesaplanan sifirin altindaysa (kutle dengesi zaten reddeder) yazilmaz.
	 */
	private static void recordRemainder(Extension extension, List<Output> outputs, BigDecimal inputQuantity) {
		String key = extension.values().stream().filter(v -> "remainder_product".equals(v.fieldKey()))
			.map(StageQueries.ExtensionValue::valueText).filter(Objects::nonNull).map(String::strip)
			.filter(v -> !v.isEmpty()).findFirst().orElse(null);
		if (key == null) {
			return;
		}
		List<Long> remainderProducts = extension.outputProducts().stream()
			.filter(o -> key.equals(o.productKey())).map(OutputProduct::productionProductId).toList();
		for (Output output : outputs) {
			if (!output.sameUnit() || !remainderProducts.contains(output.type().productionProductId())
				|| !(output.data().root() instanceof ObjectNode node)) {
				continue;
			}
			BigDecimal others = outputs.stream().filter(o -> o != output && o.sameUnit())
				.map(Output::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal calculated = inputQuantity.subtract(others);
			if (calculated.signum() <= 0) {
				continue;
			}
			node.put("calculated_quantity", calculated);
			node.put("deviation_ratio", output.quantity().subtract(calculated)
				.divide(calculated, 4, RoundingMode.HALF_UP));
		}
	}

	/** Partide uygun aktif materyal varsa o (secilen ya da tek), yoksa urun tipinden yeni girdi materyali. */
	private Material inputMaterial(StageProcess process, Extension extension, OperationData data,
		BigDecimal inputQuantity) {
		List<Material> candidates = candidateMaterials(process, extension.inputProductIds());
		if (!candidates.isEmpty()) {
			Material material = selectInput(candidates, data);
			if (inputQuantity.compareTo(material.quantity()) > 0) {
				throw new BusinessRuleException("Input quantity, seçilen material miktarından büyük olamaz.");
			}
			return material;
		}
		ProductType type = inputType(process, extension, data);
		long id = createMaterial(process, type.id(), null, inputQuantity, type.unit());
		return queries.material(id).orElseThrow();
	}

	private static Material selectInput(List<Material> candidates, OperationData data) {
		Long selected = data.id("input_material_id");
		if (selected != null) {
			return candidates.stream().filter(m -> selected.equals(m.id())).findFirst()
				.orElseThrow(() -> new BusinessRuleException("Seçilen input material bu aşama için uygun değil."));
		}
		if (candidates.size() > 1) {
			throw new BusinessRuleException("Birden fazla uygun input material bulundu. input_material_id gönderilmelidir.");
		}
		return candidates.getFirst();
	}

	private ProductType inputType(StageProcess process, Extension extension, OperationData data) {
		List<ProductType> types = candidateTypes(process, extension);
		Long selected = data.id("input_production_product_type_id");
		if (selected != null) {
			return types.stream().filter(t -> selected.equals(t.id())).findFirst()
				.orElseThrow(() -> new BusinessRuleException("Seçilen giriş ürün tipi bu aşama için uygun değil."));
		}
		if (types.isEmpty()) {
			throw new BusinessRuleException("Transformation için uygun giriş ürün tipi bulunamadı.");
		}
		if (types.size() > 1) {
			throw new BusinessRuleException(
				"Birden fazla uygun giriş ürün tipi bulundu. input_production_product_type_id gönderilmelidir.");
		}
		return types.getFirst();
	}

	private ProductType outputType(StageProcess process, ProductType inputType, Extension extension,
		OperationData output) {
		List<ProductType> batchOutputs = queries.outputTypes(process.productionBatchId());
		Long selected = output.id("production_product_type_id");
		if (selected != null) {
			List<Long> allowed = extension.outputProducts().stream().map(OutputProduct::productionProductId).toList();
			return batchOutputs.stream()
				.filter(t -> selected.equals(t.id()))
				.filter(t -> allowed.contains(t.productionProductId()))
				.filter(t -> Objects.equals(t.type(), inputType.type()) || t.type() == null)
				.findFirst()
				.orElseThrow(() -> new BusinessRuleException("Seçilen çıkış ürün tipi bu aşama için uygun değil."));
		}
		return matchingOutputType(process, outputProductId(extension, output), inputType);
	}

	private static long outputProductId(Extension extension, OperationData output) {
		Long productId = output.id("production_product_id");
		if (productId != null) {
			return productId;
		}
		String key = output.isTruthy("product_type_key") ? output.text("product_type_key") : null;
		return extension.outputProducts().stream()
			.filter(o -> o.productKey() != null && o.productKey().equals(key))
			.findFirst()
			.map(OutputProduct::productionProductId)
			.orElseThrow(() -> new BusinessRuleException("Çıkış ürün tipinin type değeri çözümlenemedi."));
	}

}
