package com.olivamore.api.quality;

import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.validation.MessageKeys;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/** Geri cagirma kaynagi secicisi: teslimatlar, tank materyalleri, dolumlar; en yeni once, en fazla 50. */
@Component
class RecallSources {

	record Option(long id, String name, String detail) {
	}

	private final JdbcClient jdbc;

	RecallSources(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Option> search(String type, String query) {
		String like = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
		String sql = switch (type) {
			case "delivery" -> """
				select b.id, b.batch_code as name,
					concat_ws(' · ', s.name, to_char(b.received_at, 'DD.MM.YYYY'), trim_scale(b.quantity_kg) || ' kg')
						as detail
				from production_batches b left join suppliers s on s.id = b.supplier_id
				where b.parent_batch_id is null
					and (cast(:q as varchar) is null or lower(b.batch_code) like :q or lower(s.name) like :q)
				order by b.received_at desc, b.id desc limit 50""";
			case "tank_material" -> """
				select m.id, concat_ws(' · ', u.name, t.name) as name,
					concat_ws(' · ', case when m.status = 'active' then 'aktif' else 'kapandı' end,
						trim_scale(round(m.quantity, 2)) || ' ' || lower(m.unit)) as detail
				from materials m
				join production_product_types t on t.id = m.production_product_type_id
				left join storage_units u on u.id = (select sm.storage_unit_id from storage_movements sm
					where sm.material_id = m.id order by sm.moved_at, sm.id limit 1)
				where m.origin_type = 'TANK'
					and (cast(:q as varchar) is null or lower(u.name) like :q or lower(t.name) like :q)
				order by m.status = 'active' desc, m.id desc limit 50""";
			case "run" -> """
				select r.id, p.name || ' · dolum #' || r.id as name,
					concat_ws(' · ', r.quantity || ' adet',
						to_char(coalesce(r.completed_at, r.created_at), 'DD.MM.YYYY'),
						r.status) as detail
				from finished_product_runs r join finished_products p on p.id = r.finished_product_id
				where r.status <> 'cancelled'
					and (cast(:q as varchar) is null or lower(p.name) like :q or cast(r.id as varchar) = :raw)
				order by coalesce(r.completed_at, r.created_at) desc, r.id desc limit 50""";
			default -> throw ValidationFailedException.of("type", MessageKeys.IN);
		};
		var statement = jdbc.sql(sql).param("q", like);
		if ("run".equals(type)) {
			statement = statement.param("raw", query == null ? "" : query.trim());
		}
		return statement.query(Option.class).list();
	}

}
