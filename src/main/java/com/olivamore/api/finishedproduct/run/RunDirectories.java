package com.olivamore.api.finishedproduct.run;

import com.olivamore.api.inventory.MaterialRunDirectory;
import com.olivamore.api.packaging.PackagingRunDirectory;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Depo ve ambalaj modullerinin gosterdigi dolum kosusu bilgileri. */
@Component
class RunDirectories implements MaterialRunDirectory, PackagingRunDirectory {

	private final JdbcClient jdbc;

	RunDirectories(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<MaterialRun> runsOf(long materialId) {
		return jdbc.sql("""
			select r.id, r.status, p.name as product_name, r.quantity,
				(select sum(m.quantity_used) from finished_product_run_materials m
					where m.finished_product_run_id = r.id and m.source_material_id = :material) as quantity_used,
				r.started_at, r.completed_at, r.created_at
			from finished_product_runs r
			left join finished_products p on p.id = r.finished_product_id
			where exists (select * from finished_product_run_materials m
				where r.id = m.finished_product_run_id and m.source_material_id = :material)
			order by r.created_at desc, r.id desc
			""").param("material", materialId).query(MaterialRun.class).list();
	}

	@Override
	public Map<Long, Run> runs(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select r.id, r.status, r.quantity, r.completed_at, p.name as product_name
			from finished_product_runs r
			left join finished_products p on p.id = r.finished_product_id
			where r.id in (:ids)
			""").param("ids", present).query(Run.class).list().stream()
			.collect(Collectors.toMap(Run::id, Function.identity()));
	}

}
