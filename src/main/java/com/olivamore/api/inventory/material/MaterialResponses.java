package com.olivamore.api.inventory.material;

import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.inventory.material.MaterialQueries.Batch;
import com.olivamore.api.inventory.material.MaterialQueries.BatchProcess;
import com.olivamore.api.inventory.material.MaterialQueries.Definition;
import com.olivamore.api.inventory.material.MaterialQueries.MaterialRow;
import com.olivamore.api.inventory.material.MaterialQueries.Product;
import com.olivamore.api.inventory.material.MaterialQueries.ProductType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Materyal cevaplari: tip (kategori, etkin yogunluk), attribute degerleri; istenirse parti, ebeveyn, proses. */
@Component
public class MaterialResponses {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.USE_LONG_FOR_INTS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.build();

	private final MaterialQueries queries;

	MaterialResponses(MaterialQueries queries) {
		this.queries = queries;
	}

	/** Verilen id'lerin sirasiyla. @param full parti, ebeveyn, yaratan ve kapatan proses de yuklenir */
	public List<MaterialResponse> materials(List<Long> ids, boolean full) {
		Map<Long, MaterialRow> rows = queries.materials(ids);
		Collection<MaterialRow> found = rows.values();
		Map<Long, ProductType> types = queries.productTypes(column(found, MaterialRow::productionProductTypeId));
		Map<Long, Product> products = queries.products(
			types.values().stream().map(ProductType::productionProductId).toList());
		Map<Long, List<MaterialResponse.AttributeValue>> values = attributeValues(rows.keySet());
		Map<Long, Batch> batches = full ? queries.batches(column(found, MaterialRow::productionBatchId)) : Map.of();
		Map<Long, MaterialRow> parents = full
			? queries.materials(column(found, MaterialRow::parentMaterialId)) : Map.of();
		Map<Long, BatchProcess> creators = full
			? queries.batchProcesses(column(found, MaterialRow::createdByBatchProcessId)) : Map.of();
		Map<Long, BatchProcess> closers = full
			? queries.batchProcesses(column(found, MaterialRow::closedByBatchProcessId)) : Map.of();
		List<MaterialResponse> out = new ArrayList<>();
		for (Long id : ids) {
			MaterialRow m = rows.get(id);
			out.add(new MaterialResponse(m.id(), m.productionBatchId(), m.productionProductTypeId(), m.parentMaterialId(),
				m.originType(), m.quantity(), m.unit(), m.status(), m.currentLocationType(), m.currentLocationId(),
				m.sourceProductionStageId(), m.createdByBatchProcessId(), m.closedByBatchProcessId(), m.note(),
				productType(lookup(types, m.productionProductTypeId()), products),
				full ? Optional.ofNullable(lookup(batches, m.productionBatchId())).map(MaterialResponses::batch) : null,
				full ? Optional.ofNullable(lookup(parents, m.parentMaterialId())).map(MaterialResponses::parent) : null,
				values.getOrDefault(m.id(), List.of()),
				full ? Optional.ofNullable(lookup(creators, m.createdByBatchProcessId())).map(MaterialResponses::process)
					: null,
				full ? Optional.ofNullable(lookup(closers, m.closedByBatchProcessId())).map(MaterialResponses::process) : null,
				m.createdAt(), m.updatedAt()));
		}
		return out;
	}

	/** Kural ve attribute degerlerindeki definition: urun-attribute eslemesi ve tanimi. */
	public Map<Long, MaterialResponse.Definition> definitions(Collection<Long> ids) {
		Map<Long, MaterialResponse.Definition> out = new LinkedHashMap<>();
		queries.definitions(ids).forEach((id, d) -> out.put(id, new MaterialResponse.Definition(d.id(),
			d.productionProductId(), d.attributeKey(), d.label(), d.dataType(), d.unit(), d.isRequired(), d.sortOrder(),
			d.description())));
		return out;
	}

	/** Kendi yogunlugu > 0 ise o, yoksa kategorinin varsayilani > 0 ise o; hic yoksa {@code null}. */
	public static Number effectiveDensity(ProductType type, Product product) {
		BigDecimal own = type.densityKgPerLitre();
		if (own != null && own.signum() > 0) {
			return Quantities.number(own);
		}
		BigDecimal fallback = product == null ? null : product.defaultDensityKgPerLitre();
		return fallback != null && fallback.signum() > 0 ? Quantities.number(fallback) : null;
	}

	/** Bilinmeyen anahtar null; bos anahtarda {@code Map.of().get(null)} NPE verir. */
	public static <T> T lookup(Map<Long, T> map, Long key) {
		return key == null ? null : map.get(key);
	}

	private Map<Long, List<MaterialResponse.AttributeValue>> attributeValues(Collection<Long> materialIds) {
		List<MaterialQueries.AttributeValueRow> rows = queries.attributeValues(materialIds);
		Map<Long, Definition> definitions = queries.definitions(
			rows.stream().map(MaterialQueries.AttributeValueRow::definitionId).toList());
		Map<Long, List<MaterialResponse.AttributeValue>> out = new LinkedHashMap<>();
		for (MaterialQueries.AttributeValueRow v : rows) {
			Definition d = definitions.get(v.definitionId());
			MaterialResponse.Definition definition = d == null ? null : new MaterialResponse.Definition(d.id(),
				d.productionProductId(), d.attributeKey(), d.label(), d.dataType(), d.unit(), d.isRequired(), d.sortOrder(),
				d.description());
			out.computeIfAbsent(v.materialId(), k -> new ArrayList<>()).add(new MaterialResponse.AttributeValue(v.id(),
				v.materialId(), v.definitionId(), v.valueBoolean(), v.valueInteger(), v.valueDecimal(), v.valueText(),
				v.valueJson() == null ? null : JSON.readValue(v.valueJson(), Object.class), definition, v.createdAt(),
				v.updatedAt()));
		}
		return out;
	}

	private static MaterialResponse.ProductType productType(ProductType t, Map<Long, Product> products) {
		if (t == null) {
			return null;
		}
		Product p = lookup(products, t.productionProductId());
		return new MaterialResponse.ProductType(t.id(), t.productionProductId(), t.name(), t.code(), t.type(), t.unit(),
			effectiveDensity(t, p), p == null ? null : product(p));
	}

	public static MaterialResponse.Product product(Product p) {
		return new MaterialResponse.Product(p.id(), p.key(), p.name(), p.description());
	}

	private static MaterialResponse.Batch batch(Batch b) {
		return new MaterialResponse.Batch(b.id(), b.batchCode(), b.status());
	}

	private static MaterialResponse.Parent parent(MaterialRow p) {
		return new MaterialResponse.Parent(p.id(), p.productionProductTypeId(), p.quantity(), p.unit(), p.status());
	}

	public static MaterialResponse.Process process(BatchProcess p) {
		return new MaterialResponse.Process(p.id(), p.productionBatchId(), p.productionStageId(), p.status());
	}

	private static List<Long> column(Collection<MaterialRow> rows,
		java.util.function.Function<MaterialRow, Long> column) {
		return rows.stream().map(column).filter(Objects::nonNull).toList();
	}

}
