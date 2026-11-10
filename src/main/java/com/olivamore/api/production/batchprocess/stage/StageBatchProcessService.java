package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.production.batchprocess.stage.StageQueries.ProductType;
import com.olivamore.api.shared.error.BusinessRuleException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Asama isleyicilerinin ortak tabani: aday urun tipleri ve materyaller, materyal yaratma. */
abstract class StageBatchProcessService {

	/** materials.quantity 4 basamak: daha kucuk farklar gurultu. */
	static final BigDecimal QUANTITY_EPSILON = new BigDecimal("0.00005");

	protected final StageQueries queries;
	protected final StockOperations stock;

	StageBatchProcessService(StageQueries queries, StockOperations stock) {
		this.queries = queries;
		this.stock = stock;
	}

	/** Partinin giris + cikis urun tipleri (sort_order), uzantinin girdi kategorilerinden olanlar, tekil. */
	List<ProductType> candidateTypes(StageProcess process, Extension extension) {
		List<ProductType> all = new ArrayList<>(queries.inputTypes(process.productionBatchId()));
		all.addAll(queries.outputTypes(process.productionBatchId()));
		return unique(all.stream().filter(t -> extension.inputProductIds().contains(t.productionProductId())).toList());
	}

	/** Partinin aktif materyalleri, tipi verilen kategorilerden (kategori yoksa hic). */
	List<Material> candidateMaterials(StageProcess process, List<Long> productIds) {
		return productIds.isEmpty() ? List.of() : queries.activeMaterials(process.productionBatchId(), productIds);
	}

	long createMaterial(StageProcess process, long typeId, Long parentId, BigDecimal quantity, String unit) {
		return stock.createBatchMaterial(process.productionBatchId(), typeId, parentId, quantity, unit,
			process.productionStageId(), process.id(), process.note());
	}

	static Extension first(List<Extension> extensions, String key) {
		return extensions.stream().filter(e -> e.enabled() && key.equals(e.key())).findFirst().orElse(null);
	}

	static Extension require(List<Extension> extensions, String key, String message) {
		Extension extension = first(extensions, key);
		if (extension == null) {
			throw new BusinessRuleException(message);
		}
		return extension;
	}

	static List<ProductType> unique(List<ProductType> types) {
		List<ProductType> out = new ArrayList<>();
		for (ProductType type : types) {
			if (out.stream().noneMatch(o -> Objects.equals(o.id(), type.id()))) {
				out.add(type);
			}
		}
		return out;
	}

	/** Sondaki sifirlar olmadan: {@code 5000.5}, {@code 6000}. */
	static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	/** {@code null} ile bos metin ayni sayilir. */
	static boolean sameText(String a, String b) {
		return Objects.requireNonNullElse(a, "").equals(Objects.requireNonNullElse(b, ""));
	}

}
