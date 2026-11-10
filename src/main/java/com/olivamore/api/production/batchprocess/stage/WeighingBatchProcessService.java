package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.production.batchprocess.stage.StageQueries.ProductType;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Partideki urunun miktari: materyal yoksa yaratilir, varsa miktari dusurulur (artirilamaz). */
@Service
class WeighingBatchProcessService extends StageBatchProcessService {

	WeighingBatchProcessService(StageQueries queries, StockOperations stock) {
		super(queries, stock);
	}

	void weighing(StageProcess process, List<Extension> extensions, OperationData data) {
		Extension extension = require(extensions, "weighing", "Weighing extension bulunamadı.");
		ProductType type = productType(process, extension, data);
		BigDecimal quantity = data.quantity("quantity");
		if (quantity.signum() <= 0) {
			throw new BusinessRuleException("Weighing işlemi için quantity zorunludur.");
		}
		String unit = data.text("unit") != null ? data.text("unit") : type.unit();
		if (!Objects.equals(unit, type.unit())) {
			throw new BusinessRuleException("Tartım birimi ürün birimi ile uyuşmuyor.");
		}
		Material material = queries.activeMaterialOfType(process.productionBatchId(), type.id()).orElse(null);
		if (material == null) {
			createMaterial(process, type.id(), null, quantity, unit);
			return;
		}
		if (quantity.subtract(material.quantity()).compareTo(QUANTITY_EPSILON) > 0) {
			throw new BusinessRuleException("Tartım kayıtlı miktarı artıramaz: bu üründen " + plain(material.quantity()) + " "
				+ material.unit() + " görünüyor, " + plain(quantity) + " " + material.unit() + " girildi. Üretim aşamaları "
				+ "ürünü azaltır, çoğaltmaz. Terazi daha fazlasını gösteriyorsa ürünün "
				+ "sisteme girildiği aşamadaki miktar yanlış demektir.");
		}
		stock.recordWeighing(material.id(), quantity, unit, process.productionStageId(), process.productionBatchId(),
			process.note());
	}

	private ProductType productType(StageProcess process, Extension extension, OperationData data) {
		List<ProductType> candidates = candidateTypes(process, extension);
		Long selected = data.id("production_product_type_id");
		if (selected != null) {
			return candidates.stream().filter(t -> selected.equals(t.id())).findFirst().orElseThrow(
				() -> new BusinessRuleException("Seçilen ürün bu weighing aşaması için batch içinde uygun değil."));
		}
		if (candidates.isEmpty()) {
			throw new BusinessRuleException("Bu weighing aşaması için batch içinde uygun ürün bulunamadı.");
		}
		if (candidates.size() > 1) {
			throw new BusinessRuleException(
				"Bu weighing aşaması için birden fazla uygun ürün bulundu. production_product_type_id gönderilmelidir.");
		}
		return candidates.getFirst();
	}

}
