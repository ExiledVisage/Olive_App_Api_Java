package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.production.batchprocess.stage.StageQueries.ProductType;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;

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

	/**
	 * Uzantinin girdi kategorilerindeki (tanimliysa) aktif parti materyali; secilen ya da tek.
	 * @param label hata mesajinda islemin adi
	 */
	Material batchMaterial(StageProcess process, Extension extension, OperationData data, String label) {
		List<Long> categories = extension == null ? List.of() : extension.inputProductIds();
		Long selected = data.id("material_id");
		if (selected != null) {
			return selectedMaterial(process, categories, selected);
		}
		List<Material> materials = categories.isEmpty() ? queries.activeMaterials(process.productionBatchId())
			: candidateMaterials(process, categories);
		if (materials.isEmpty()) {
			throw new BusinessRuleException(label + " için aktif material bulunamadı.");
		}
		if (materials.size() > 1) {
			throw new BusinessRuleException("Birden fazla aktif material bulundu. material_id gönderilmelidir.");
		}
		return materials.getFirst();
	}

	private Material selectedMaterial(StageProcess process, List<Long> categories, long id) {
		Material material = queries.material(id).orElseThrow(() -> new ModelNotFoundException("Material", id));
		if (material.productionBatchId() == null) {
			throw new BusinessRuleException("Seçilen material bir partiye ait değil (ör. son tanktaki karışım); üretim "
				+ "içinden üzerinde işlem yapılamaz.");
		}
		if (!Objects.equals(material.productionBatchId(), process.productionBatchId())) {
			throw new BusinessRuleException("Seçilen material bu batch’e ait değil.");
		}
		ProductType type = queries.productType(material.productionProductTypeId()).orElseThrow();
		if (!categories.isEmpty() && !categories.contains(type.productionProductId())) {
			throw new BusinessRuleException("Seçilen material (" + type.name() + ") bu aşamanın girdi ürünlerinden değil.");
		}
		return material;
	}

	/**
	 * Partinin cikti tiplerinden verilen kategoride, girdinin cesidiyle (type) ayni olan; yoksa cesitsiz olan. Tek
	 * olmali.
	 */
	ProductType matchingOutputType(StageProcess process, long productId, ProductType inputType) {
		List<ProductType> sameProduct = unique(queries.outputTypes(process.productionBatchId()).stream()
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
