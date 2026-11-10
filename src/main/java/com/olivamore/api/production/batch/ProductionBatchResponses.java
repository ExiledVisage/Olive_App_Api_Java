package com.olivamore.api.production.batch;

import com.olivamore.api.inventory.MaterialResources;
import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.production.batch.BatchQueries.LineProductRow;
import com.olivamore.api.production.batch.BatchQueries.LineRow;
import com.olivamore.api.production.batch.BatchQueries.MaterialLink;
import com.olivamore.api.production.batch.ProductionBatchResponse.BatchType;
import com.olivamore.api.production.batch.ProductionBatchResponse.Line;
import com.olivamore.api.production.batch.ProductionBatchResponse.LineProduct;
import com.olivamore.api.production.batchprocess.BatchProcess;
import com.olivamore.api.production.batchprocess.BatchProcessRepository;
import com.olivamore.api.production.batchprocess.BatchProcessResponse;
import com.olivamore.api.production.batchprocess.BatchProcessResponses;
import com.olivamore.api.production.batchprocess.ProcessResponseQueries;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Parti cevaplari: hat, giris/cikis tipleri, prosesler, materyaller. */
@Component
class ProductionBatchResponses {

	private final ProductionBatchRepository batches;
	private final BatchInputTypeRepository inputs;
	private final BatchOutputTypeRepository outputs;
	private final BatchProcessRepository processes;
	private final BatchProcessResponses processResponses;
	private final BatchQueries queries;
	private final ProcessResponseQueries users;
	private final MaterialResources materials;

	ProductionBatchResponses(ProductionBatchRepository batches, BatchInputTypeRepository inputs,
		BatchOutputTypeRepository outputs, BatchProcessRepository processes, BatchProcessResponses processResponses,
		BatchQueries queries, ProcessResponseQueries users, MaterialResources materials) {
		this.batches = batches;
		this.inputs = inputs;
		this.outputs = outputs;
		this.processes = processes;
		this.processResponses = processResponses;
		this.queries = queries;
		this.users = users;
		this.materials = materials;
	}

	ProductionBatchResponse detail(long id) {
		return list(List.of(batches.findById(id).orElseThrow(() -> new ModelNotFoundException("ProductionBatch", id))))
			.getFirst();
	}

	List<ProductionBatchResponse> list(List<ProductionBatch> rows) {
		List<Long> ids = rows.stream().map(ProductionBatch::getId).toList();
		Map<Long, List<BatchType>> inputTypes = types(inputs.findByProductionBatchIdInOrderBySortOrderAsc(ids));
		Map<Long, List<BatchType>> outputTypes = types(outputs.findByProductionBatchIdInOrderBySortOrderAsc(ids));
		Map<Long, Line> lines = lines(rows.stream().map(ProductionBatch::getProductionLineId).distinct().toList());
		Map<Long, BatchProcessResponse.UserSummary> creators = users.users(
			present(rows.stream().map(ProductionBatch::getCreatedByUserId)));
		Map<Long, BatchQueries.Supplier> suppliers = queries.suppliers(
			present(rows.stream().map(ProductionBatch::getSupplierId)));
		Map<Long, BatchQueries.SupplierField> fields = queries.supplierFields(
			present(rows.stream().map(ProductionBatch::getSupplierFieldId)));
		Map<Long, List<BatchProcessResponse>> processMap = processes(ids);
		Map<Long, List<MaterialResponse>> materialMap = materials(ids);

		List<ProductionBatchResponse> out = new ArrayList<>();
		for (ProductionBatch b : rows) {
			out.add(new ProductionBatchResponse(b.getId(), b.getBatchCode(), b.getProductionLineId(), b.getSupplierId(),
				b.getSupplierFieldId(), suppliers.get(b.getSupplierId()), fields.get(b.getSupplierFieldId()),
				b.getCreatedByUserId(), b.getStatus(), b.getQuantityKg(), b.getReceivedAt(), b.getStartedAt(),
				b.getCompletedAt(), b.getNote(), inputTypes.getOrDefault(b.getId(), List.of()),
				outputTypes.getOrDefault(b.getId(), List.of()), lines.get(b.getProductionLineId()),
				creators.get(b.getCreatedByUserId()), processMap.getOrDefault(b.getId(), List.of()),
				materialMap.getOrDefault(b.getId(), List.of()), b.getCreatedAt(), b.getUpdatedAt()));
		}
		return out;
	}

	private static List<Long> present(Stream<Long> ids) {
		return ids.filter(Objects::nonNull).distinct().toList();
	}

	private Map<Long, List<BatchProcessResponse>> processes(List<Long> batchIds) {
		List<BatchProcess> rows = processes.findByProductionBatchIdIn(batchIds);
		List<BatchProcessResponse> responses = processResponses.ofBatchProcesses(rows);
		Map<Long, List<BatchProcessResponse>> out = new LinkedHashMap<>();
		responses.forEach(r -> out.computeIfAbsent(r.productionBatchId(), k -> new ArrayList<>()).add(r));
		return out;
	}

	private Map<Long, List<MaterialResponse>> materials(List<Long> batchIds) {
		List<MaterialLink> links = queries.materialsOfBatches(batchIds);
		List<MaterialResponse> responses = materials.materials(links.stream().map(MaterialLink::id).toList(),
			MaterialResources.Load.FULL);
		Map<Long, List<MaterialResponse>> out = new LinkedHashMap<>();
		for (int i = 0; i < links.size(); i++) {
			out.computeIfAbsent(links.get(i).productionBatchId(), k -> new ArrayList<>()).add(responses.get(i));
		}
		return out;
	}

	private <T extends BatchProductType> Map<Long, List<BatchType>> types(List<T> rows) {
		Map<Long, BatchQueries.ProductType> types = queries.productTypes(
			rows.stream().map(BatchProductType::getProductionProductTypeId).distinct().toList());
		Map<Long, List<BatchType>> out = new LinkedHashMap<>();
		for (T row : rows) {
			out.computeIfAbsent(row.getProductionBatchId(), k -> new ArrayList<>()).add(new BatchType(row.getId(),
				row.getProductionBatchId(), row.getProductionProductTypeId(), row.getSortOrder(),
				types.get(row.getProductionProductTypeId())));
		}
		return out;
	}

	private Map<Long, Line> lines(List<Long> lineIds) {
		Map<Long, List<LineProduct>> inputProducts = lineProducts(queries.lineInputs(lineIds));
		Map<Long, List<LineProduct>> outputProducts = lineProducts(queries.lineOutputs(lineIds));
		Map<Long, LineRow> rows = queries.lines(lineIds);
		return rows.values().stream().collect(Collectors.toMap(LineRow::id, row -> new Line(row.id(), row.name(),
			row.description(), inputProducts.getOrDefault(row.id(), List.of()),
			outputProducts.getOrDefault(row.id(), List.of()))));
	}

	private Map<Long, List<LineProduct>> lineProducts(List<LineProductRow> rows) {
		Map<Long, BatchQueries.Product> products = queries.products(
			rows.stream().map(LineProductRow::productionProductId).distinct().toList());
		Map<Long, List<LineProduct>> out = new LinkedHashMap<>();
		for (LineProductRow row : rows) {
			out.computeIfAbsent(row.productionLineId(), k -> new ArrayList<>()).add(new LineProduct(row.id(),
				row.productionLineId(), row.productionProductId(), row.sortOrder(), products.get(row.productionProductId())));
		}
		return out;
	}

}
