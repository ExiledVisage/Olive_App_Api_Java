package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Randiman: on dinlendirme tankindan son tanka giden (filtrelenmis) yag / sikima giren zeytin x 100, kilo ile.
 * Girilen {@code yield_percent} esastir; hesaplanabiliyorsa hesaplanan deger, sapma orani ve hesabin girdileri
 * asamanin verisine yazilir.
 */
@Service
class YieldBatchProcessService extends StageBatchProcessService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	YieldBatchProcessService(StageQueries queries, StockOperations stock) {
		super(queries, stock);
	}

	/**
	 * @param oliveKg sikima giren zeytin; sikim yoksa null
	 * @param filteredOilKg on dinlendirmeden son tanka giden yag; Boşalt yapilmadiysa null
	 * @param yieldPercent ikisi de varsa randiman (iki basamak); yoksa null
	 */
	record Calculation(BigDecimal oliveKg, BigDecimal filteredOilKg, BigDecimal yieldPercent) {
	}

	Calculation calculate(long batchId) {
		BigDecimal olive = queries.pressedOliveKg(batchId);
		BigDecimal oil = queries.filteredOilKg(batchId);
		BigDecimal yield = olive == null || oil == null || olive.signum() <= 0 ? null
			: oil.multiply(HUNDRED).divide(olive, 2, RoundingMode.HALF_UP);
		return new Calculation(olive, oil, yield);
	}

	void yield(StageProcess process, List<Extension> extensions, OperationData data) {
		BigDecimal entered = data.quantity("yield_percent");
		if (entered.signum() <= 0) {
			throw new BusinessRuleException("Randıman için yield_percent zorunludur.");
		}
		Calculation calculation = calculate(process.productionBatchId());
		if (calculation.yieldPercent() == null || !(data.root() instanceof ObjectNode node)) {
			return;
		}
		BigDecimal calculated = calculation.yieldPercent();
		node.put("olive_kg", calculation.oliveKg().setScale(2, RoundingMode.HALF_UP));
		node.put("filtered_oil_kg", calculation.filteredOilKg().setScale(2, RoundingMode.HALF_UP));
		node.put("calculated_yield_percent", calculated);
		node.put("deviation_ratio", calculated.signum() == 0 ? BigDecimal.ZERO
			: entered.subtract(calculated).divide(calculated, 4, RoundingMode.HALF_UP));
	}

}
