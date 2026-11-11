package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.product.UnitConversion;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.production.batchprocess.stage.StageQueries.OutputProduct;
import com.olivamore.api.production.batchprocess.stage.StageQueries.ProductType;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * On dinlendirme (C3-C5): partideki urun on dinlendirme tankina girer, asama tank bosaltilana kadar dinlenmede
 * bekler. Bosaltmada tanka giren ile cekilen arasindaki fark tortu olur; hangi urun oldugu uzantinin cikti urunundan.
 */
@Service
class PreSettlingBatchProcessService extends StageBatchProcessService {

	private static final String KEY = "pre_settling";

	PreSettlingBatchProcessService(StageQueries queries, StockOperations stock) {
		super(queries, stock);
	}

	/** Tortu urunu girisle birlikte dogrulanir ki hata bosaltmada degil gonderimde ortaya ciksin. */
	void preSettling(StageProcess process, List<Extension> extensions, OperationData data) {
		Extension extension = require(extensions, KEY, "Pre-settling extension bulunamadı.");
		Long unitId = data.id("storage_unit_id");
		BigDecimal quantity = data.quantity("quantity");
		if (unitId == null || quantity.signum() <= 0) {
			throw new BusinessRuleException("Ön dinlendirme için storage_unit_id ve quantity zorunludur.");
		}
		Material material = batchMaterial(process, extension, data, "Ön dinlendirme");
		residueType(process, extension, material);
		String note = data.text("note") != null ? data.text("note") : process.note();
		stock.enterPreSettling(unitId, material.id(), quantity, process.id(), note);
	}

	void cancel(long batchProcessId) {
		stock.cancelPreSettling(batchProcessId, "Ön dinlendirme girişi iptal edildi.");
	}

	/** Bosaltilan parti materyalinin tortusu, materyalin cocugu olarak partide acilir; fark sifirsa acilmaz. */
	void residue(StageProcess process, List<Extension> extensions, long materialId, BigDecimal residueKg) {
		if (residueKg.signum() <= 0) {
			return;
		}
		Extension extension = require(extensions, KEY, "Pre-settling extension bulunamadı.");
		Material material = queries.material(materialId).orElseThrow();
		ProductType type = residueType(process, extension, material);
		BigDecimal quantity = UnitConversion.convert(residueKg, UnitConversion.KG, type.unit(), type.density(),
			type.name());
		createMaterial(process, type.id(), materialId, quantity, type.unit());
	}

	/** Uzantinin (ilk) cikti urunu, partinin cikti tiplerinden giren urunun cesidine uyani. */
	private ProductType residueType(StageProcess process, Extension extension, Material material) {
		Long productId = extension.outputProducts().stream().map(OutputProduct::productionProductId).findFirst()
			.orElseThrow(() -> new BusinessRuleException("Ön dinlendirme aşamasında tortunun hangi ürün olacağı "
				+ "tanımlı değil; aşama ayarına çıktı ürünü eklenmeli."));
		ProductType inputType = queries.productType(material.productionProductTypeId()).orElseThrow();
		return matchingOutputType(process, productId, inputType);
	}

}
