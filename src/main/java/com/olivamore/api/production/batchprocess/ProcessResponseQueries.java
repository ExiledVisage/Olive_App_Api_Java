package com.olivamore.api.production.batchprocess;

import com.olivamore.api.production.batchprocess.BatchProcessResponse.BatchSummary;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.UserSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Proses cevabinin baska modullerden okunan kisimlari. */
@Component
public class ProcessResponseQueries {

	record MaterialLink(Long id, Long ownerId) {
	}

	private final JdbcClient jdbc;

	ProcessResponseQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Map<Long, UserSummary> users(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Collections.emptyMap();
		}
		return jdbc.sql("select id, name, email from users where id in (:ids)").param("ids", ids)
			.query(UserSummary.class).list().stream().collect(Collectors.toMap(UserSummary::id, Function.identity()));
	}

	Map<Long, BatchSummary> batches(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Collections.emptyMap();
		}
		return jdbc.sql("select id, batch_code, status from production_batches where id in (:ids)").param("ids", ids)
			.query(BatchSummary.class).list().stream().collect(Collectors.toMap(BatchSummary::id, Function.identity()));
	}

	/** Proses tarafindan yaratilan materyaller, satir sirasiyla. */
	Map<Long, List<Long>> materialsCreatedBy(Collection<Long> processIds) {
		return links("created_by_batch_process_id", processIds);
	}

	/** Proses tarafindan kapatilan materyaller, satir sirasiyla. */
	Map<Long, List<Long>> materialsClosedBy(Collection<Long> processIds) {
		return links("closed_by_batch_process_id", processIds);
	}

	private Map<Long, List<Long>> links(String column, Collection<Long> processIds) {
		if (processIds.isEmpty()) {
			return Collections.emptyMap();
		}
		List<MaterialLink> rows = jdbc.sql("select id, " + column + " as owner_id from materials where " + column + " in (:ids)")
			.param("ids", processIds).query(MaterialLink.class).list();
		return rows.stream().collect(Collectors.groupingBy(MaterialLink::ownerId, LinkedHashMap::new,
			Collectors.mapping(MaterialLink::id, Collectors.toList())));
	}

}
