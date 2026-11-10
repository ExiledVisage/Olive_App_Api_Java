package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.production.batchprocess.stage.StageQueries.OutputProduct;
import com.olivamore.api.production.batchprocess.stage.StageQueries.ProductType;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Girdi materyali (yoksa tipinden yaratilir) tuketilir, ciktilar yaratilir; ayni birimdeki ciktilar girdiyi asamaz. */
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
			if (Objects.equals(unit, input.unit())) {
				sameUnitTotal = sameUnitTotal.add(quantity);
			}
			createMaterial(process, type.id(), input.id(), quantity, unit);
		}

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
		long productId = outputProductId(extension, output);
		List<ProductType> sameProduct = unique(batchOutputs.stream()
			.filter(t -> Objects.equals(t.productionProductId(), productId)).toList());
		List<ProductType> candidates = sameProduct.stream().filter(t -> sameText(t.type(), inputType.type())).toList();
		if (candidates.isEmpty()) {
			candidates = sameProduct.stream().filter(t -> t.type() == null).toList();
		}
		if (candidates.isEmpty()) {
			throw new BusinessRuleException("Giriş ürünüyle uyumlu çıkış ürün tipi bulunamadı.");
		}
		if (candidates.size() > 1) {
			throw new BusinessRuleException(
				"Birden fazla uygun çıkış ürün tipi bulundu. production_product_type_id gönderilmelidir.");
		}
		return candidates.getFirst();
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
