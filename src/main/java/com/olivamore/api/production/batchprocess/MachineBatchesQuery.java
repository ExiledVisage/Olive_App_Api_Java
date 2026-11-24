package com.olivamore.api.production.batchprocess;

import com.olivamore.api.machine.MachineBatches;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Makine sekmesi icin adimlarin partileri. Yag ve randiman {@code StageQueries.filteredOilKg} ve randiman uzantisiyla
 * ayni tanimda: on dinlendirme tankindan cikan yag (litre yogunlukla kg), tamamlanmis randiman asamasinin degeri.
 */
@Component
class MachineBatchesQuery implements MachineBatches {

	private final JdbcClient jdbc;

	MachineBatchesQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<Long, Batch> batches(Collection<Long> batchProcessIds) {
		if (batchProcessIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select bp.id as batch_process_id, pb.id as batch_id, pb.batch_code,
				case when bp.status = 'completed' then (bp.operation_data ->> 'input_quantity')::numeric end
					as pressed_kg,
				(select sum(case when sm.unit = 'LITRE'
						then sm.quantity * coalesce(nullif(t.density_kg_per_litre, 0), p.default_density_kg_per_litre)
						else sm.quantity end)
					from storage_movements sm
					join batch_processes mbp on mbp.id = sm.batch_process_id
					join storage_units u on u.id = sm.storage_unit_id
					join materials m on m.id = sm.material_id
					join production_product_types t on t.id = m.production_product_type_id
					join production_products p on p.id = t.production_product_id
					where mbp.production_batch_id = pb.id and sm.movement_type = 'TRANSFER_OUT'
						and u.kind = 'pre_settling') as oil_kg,
				(select (y.operation_data ->> 'yield_percent')::numeric from batch_processes y
					where y.production_batch_id = pb.id and y.status = 'completed' and exists (
						select 1 from production_stage_extensions e
						join stage_extension_types et on et.id = e.stage_extension_type_id
						where e.production_stage_id = y.production_stage_id and e.is_enabled and et.key = 'yield')
					order by y.id limit 1) as yield_percent
			from batch_processes bp
			join production_batches pb on pb.id = bp.production_batch_id
			where bp.id in (:ids)
			""").param("ids", batchProcessIds).query(Batch.class).list().stream()
			.collect(Collectors.toMap(Batch::batchProcessId, Function.identity()));
	}

}
