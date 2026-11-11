package com.olivamore.api.production.batch;

import com.olivamore.api.inventory.BatchDirectory;
import com.olivamore.api.product.ProductDirectory;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Depo modulunun materyal cevaplarinda ve tank kurallarinda istedigi parti bilgileri. */
@Component
class ProductionBatchDirectory implements BatchDirectory {

	private record OutputRow(Long productionProductTypeId) {
	}

	private final JdbcClient jdbc;
	private final ProductDirectory products;

	ProductionBatchDirectory(JdbcClient jdbc, ProductDirectory products) {
		this.jdbc = jdbc;
		this.products = products;
	}

	@Override
	public Map<Long, Batch> batches(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, batch_code, status from production_batches where id in (:ids)")
			.param("ids", present).query(Batch.class).list().stream().collect(Collectors.toMap(Batch::id, Function.identity()));
	}

	@Override
	public Map<Long, BatchProcess> batchProcesses(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select id, production_batch_id, production_stage_id, status from batch_processes where id in (:ids)
			""").param("ids", present).query(BatchProcess.class).list().stream()
			.collect(Collectors.toMap(BatchProcess::id, Function.identity()));
	}

	@Override
	public boolean batchProcessExists(long id) {
		return jdbc.sql("select count(*) from batch_processes where id = :id").param("id", id)
			.query(Long.class).single() > 0;
	}

	@Override
	public Optional<String> batchCode(long batchId) {
		return jdbc.sql("select batch_code from production_batches where id = :id").param("id", batchId)
			.query(String.class).optional();
	}

	@Override
	public List<BatchOutput> batchOutputs(long batchId) {
		List<OutputRow> rows = jdbc.sql("""
			select production_product_type_id from production_batch_output_production_product_types
			where production_batch_id = :batch order by sort_order asc, id asc
			""").param("batch", batchId).query(OutputRow.class).list();
		Map<Long, ProductDirectory.ProductType> types = products.productTypes(
			rows.stream().map(OutputRow::productionProductTypeId).toList());
		return rows.stream().map(row -> {
			ProductDirectory.ProductType type = types.get(row.productionProductTypeId());
			return new BatchOutput(row.productionProductTypeId(), type == null ? null : type.name());
		}).toList();
	}

	@Override
	public List<Long> batchIdsWithCode(String like) {
		return jdbc.sql("select id from production_batches where batch_code::text ilike :like").param("like", like)
			.query(Long.class).list();
	}

}
