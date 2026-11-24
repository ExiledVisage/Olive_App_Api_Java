package com.olivamore.api.production.batch;

import com.olivamore.api.finishedproduct.RunOriginDirectory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Izlenebilirlik sayfasi icin teslimatlar: gruplar teslimatina cikar; tedarikci, tarla ve girdi cesitleri. */
@Component
class RunOriginReadModel implements RunOriginDirectory {

	private final ProductionBatchRepository batches;
	private final BatchQueries queries;
	private final SupplierDeliveryReadModel deliveries;

	RunOriginReadModel(ProductionBatchRepository batches, BatchQueries queries, SupplierDeliveryReadModel deliveries) {
		this.batches = batches;
		this.queries = queries;
		this.deliveries = deliveries;
	}

	@Override
	public List<Delivery> deliveries(Collection<Long> batchIds) {
		List<Long> ids = batchIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return List.of();
		}
		List<Long> deliveryIds = batches.findAllById(ids).stream()
			.map(b -> b.isGroup() ? b.getParentBatchId() : b.getId()).distinct().toList();
		List<ProductionBatch> rows = batches.findAllById(deliveryIds).stream()
			.sorted(Comparator.comparing(ProductionBatch::getReceivedAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(ProductionBatch::getId))
			.toList();
		Map<Long, BatchQueries.Supplier> suppliers = queries.suppliers(rows.stream()
			.map(ProductionBatch::getSupplierId).filter(Objects::nonNull).distinct().toList());
		Map<Long, BatchQueries.SupplierField> fields = queries.supplierFields(rows.stream()
			.map(ProductionBatch::getSupplierFieldId).filter(Objects::nonNull).distinct().toList());
		Map<Long, List<String>> cultivars = deliveries.inputProductNames(deliveryIds);
		return rows.stream().map(b -> {
			BatchQueries.Supplier supplier = suppliers.get(b.getSupplierId());
			BatchQueries.SupplierField field = fields.get(b.getSupplierFieldId());
			return new Delivery(b.getId(), b.getBatchCode(), b.getReceivedAt(),
				supplier == null ? null : supplier.name(), field == null ? null : field.code(),
				field == null ? null : field.location(), cultivars.getOrDefault(b.getId(), List.of()));
		}).toList();
	}

}
