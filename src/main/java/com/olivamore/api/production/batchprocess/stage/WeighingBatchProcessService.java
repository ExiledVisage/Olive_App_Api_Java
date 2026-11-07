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

/** Laravel {@code WeighingBatchProcessService}: partideki urunun miktari; materyal yoksa yaratilir, varsa miktari dusurulur (artirilamaz). */
@Service
class WeighingBatchProcessService extends StageBatchProcessService {

	WeighingBatchProcessService(Db db, StockOperations stock) {
		super(db, stock);
	}

	void weighing(Row process, List<Extension> extensions, Map<String, Object> data) {
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

}
