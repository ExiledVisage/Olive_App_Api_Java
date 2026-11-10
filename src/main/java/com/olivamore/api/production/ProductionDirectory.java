package com.olivamore.api.production;

import com.olivamore.api.productionline.ProductionLineResources;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Bildirimlerde gorunen parti ve proses bilgileri. */
@Component
public class ProductionDirectory {

	private record ProcessRow(String batchCode, Long productionStageId) {
	}

	private final JdbcClient jdbc;
	private final ProductionLineResources lines;

	ProductionDirectory(JdbcClient jdbc, ProductionLineResources lines) {
		this.jdbc = jdbc;
		this.lines = lines;
	}

	public Optional<String> batchCode(long id) {
		return jdbc.sql("select batch_code from production_batches where id = :id").param("id", id)
			.query(String.class).optional();
	}

	/** "Parti - asama" etiketi; proses yoksa bos. */
	public Optional<String> processLabel(long id) {
		return jdbc.sql("""
			select b.batch_code, p.production_stage_id
			from batch_processes p left join production_batches b on b.id = p.production_batch_id
			where p.id = :id
			""").param("id", id).query(ProcessRow.class).optional().map(this::label);
	}

	private String label(ProcessRow row) {
		var stage = row.productionStageId() == null ? null
			: lines.stages(List.of(row.productionStageId())).get(row.productionStageId());
		return (row.batchCode() != null ? row.batchCode() : "Parti") + " - "
			+ (stage != null && stage.name() != null ? stage.name() : "Aşama");
	}

}
