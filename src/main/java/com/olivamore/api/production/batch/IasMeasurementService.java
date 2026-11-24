package com.olivamore.api.production.batch;

import com.olivamore.api.inventory.MaterialDirectory;
import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * IAS olcumu (docs/GREENOPS_FEATURES.md 3): asamadan bagimsiz, partinin zeytin ya da pirina materyaline
 * attribute olarak yazilir (yag orani, nem, KPY, numune no; zeytinde asitlik ve endustriyel randiman). KPY
 * verilmezse yag / (100 - nem) x 100. Sonradan gelen laboratuvar sonucu icin tuketilmis materyale de yazilabilir;
 * tekrar olculurse degerler degisir. Pirina sikimda olustugu icin pirina olcumu sikimdan once yapilamaz.
 */
@Service
class IasMeasurementService {

	private static final String OLIVE = "olive";
	private static final String POMACE = "pomace";
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final ProductionBatchRepository batches;
	private final MaterialDirectory materials;
	private final ProductDirectory products;
	private final StockOperations stock;

	IasMeasurementService(ProductionBatchRepository batches, MaterialDirectory materials, ProductDirectory products,
		StockOperations stock) {
		this.batches = batches;
		this.materials = materials;
		this.products = products;
		this.stock = stock;
	}

	@Transactional
	void record(long batchId, IasMeasurementRequest request) {
		ProductionBatch batch = batches.findById(batchId)
			.orElseThrow(() -> new ModelNotFoundException("ProductionBatch", batchId));
		String kind = request.kind();
		ValidationErrors errors = new ValidationErrors();
		if (!OLIVE.equals(kind) && !POMACE.equals(kind)) {
			errors.add("kind", MessageKeys.IN);
		}
		if (POMACE.equals(kind) && request.acidity() != null) {
			errors.add("acidity", "Pirinada asitlik ölçülmez.");
		}
		if (POMACE.equals(kind) && request.industrialYield() != null) {
			errors.add("industrial_yield", "Endüstriyel randıman yalnızca zeytinde ölçülür.");
		}
		errors.throwIfAny();

		Target target = target(batch, kind, request.materialId());
		BigDecimal kpy = request.kpy() != null ? request.kpy() : request.oilRatio().multiply(HUNDRED)
			.divide(HUNDRED.subtract(request.moisture()), 2, RoundingMode.HALF_UP);
		decimal(target, "oil_ratio", request.oilRatio());
		decimal(target, "moisture", request.moisture());
		decimal(target, "kpy", kpy);
		if (request.acidity() != null) {
			decimal(target, "acidity", request.acidity());
		}
		if (request.industrialYield() != null) {
			decimal(target, "industrial_yield", request.industrialYield());
		}
		if (request.sampleNo() != null) {
			write(target, "sample_no", new StockOperations.AttributeValue(null, null, null, request.sampleNo(), null));
		}
	}

	private record Target(long materialId, long productId) {
	}

	/**
	 * Partinin o urundeki materyali: verilen, yoksa tek aktif olan, aktif yoksa en son olusan (sikimdan sonra
	 * gelen zeytin sonucu). Birden fazla aktif varsa {@code material_id} istenir.
	 */
	private Target target(ProductionBatch batch, String kind, Long materialId) {
		List<Long> ids = materials.ofBatches(List.of(batch.getId())).stream().map(MaterialDirectory.BatchMaterial::id)
			.toList();
		Map<Long, MaterialDirectory.Material> rows = materials.materials(ids);
		Map<Long, ProductDirectory.ProductType> types = products.productTypes(rows.values().stream()
			.map(MaterialDirectory.Material::productionProductTypeId).distinct().toList());
		Map<Long, ProductDirectory.Product> productRows = products.products(types.values().stream()
			.map(ProductDirectory.ProductType::productionProductId).distinct().toList());
		List<MaterialDirectory.Material> candidates = rows.values().stream().filter(m -> {
			ProductDirectory.ProductType type = types.get(m.productionProductTypeId());
			ProductDirectory.Product product = type == null ? null : productRows.get(type.productionProductId());
			return product != null && kind.equals(product.key());
		}).sorted(Comparator.comparing(MaterialDirectory.Material::id).reversed()).toList();

		MaterialDirectory.Material chosen;
		if (materialId != null) {
			chosen = candidates.stream().filter(m -> Objects.equals(m.id(), materialId)).findFirst()
				.orElseThrow(() -> ValidationFailedException.of("material_id",
					"Seçilen materyal bu partinin " + label(kind) + " materyali değil."));
		}
		else {
			if (candidates.isEmpty()) {
				throw new BusinessRuleException(POMACE.equals(kind)
					? "'" + batch.getBatchCode() + "' partisinde pirina yok; pirina ölçümü sıkımdan sonra girilir."
					: "'" + batch.getBatchCode() + "' partisinde zeytin yok.");
			}
			List<MaterialDirectory.Material> active = candidates.stream()
				.filter(m -> "active".equals(m.status())).toList();
			if (active.size() > 1) {
				throw ValidationFailedException.of("material_id", "Partide birden fazla " + label(kind)
					+ " materyali var; material_id gönderilmelidir.");
			}
			chosen = active.isEmpty() ? candidates.getFirst() : active.getFirst();
		}
		return new Target(chosen.id(), types.get(chosen.productionProductTypeId()).productionProductId());
	}

	private void decimal(Target target, String key, BigDecimal value) {
		write(target, key, new StockOperations.AttributeValue(null, null, value, null, null));
	}

	private void write(Target target, String key, StockOperations.AttributeValue value) {
		ProductDirectory.AttributeMapping mapping = products.attributeMappingByKey(target.productId(), key)
			.orElseThrow(() -> new BusinessRuleException("Ürün tanımında '" + key + "' özelliği yok; ürün "
				+ "ayarlarından eklenmeli."));
		stock.recordAttribute(target.materialId(), mapping.id(), value);
	}

	private static String label(String kind) {
		return POMACE.equals(kind) ? "pirina" : "zeytin";
	}

}
