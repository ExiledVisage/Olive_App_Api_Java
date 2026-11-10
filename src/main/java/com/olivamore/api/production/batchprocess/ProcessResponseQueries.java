package com.olivamore.api.production.batchprocess;

import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.inventory.MaterialDirectory;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.BatchSummary;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.UserSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Proses cevabinin baska modullerden okunan kisimlari. */
@Component
public class ProcessResponseQueries {

	private final JdbcClient jdbc;
	private final UserDirectory userDirectory;
	private final MaterialDirectory materials;

	ProcessResponseQueries(JdbcClient jdbc, UserDirectory userDirectory, MaterialDirectory materials) {
		this.jdbc = jdbc;
		this.userDirectory = userDirectory;
		this.materials = materials;
	}

	public Map<Long, UserSummary> users(Collection<Long> ids) {
		return userDirectory.users(ids).values().stream().collect(Collectors.toMap(UserDirectory.User::id,
			u -> new UserSummary(u.id(), u.name(), u.email())));
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
		return materials.createdBy(processIds);
	}

	/** Proses tarafindan kapatilan materyaller, satir sirasiyla. */
	Map<Long, List<Long>> materialsClosedBy(Collection<Long> processIds) {
		return materials.closedBy(processIds);
	}

}
