package com.olivamore.api.supplier;

import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tedarikciler ve tarlalari: diger modullerin okudugu tek kapi. */
@Component
public class SupplierDirectory {

	public record Supplier(Long id, String name, String code) {
	}

	public record Field(Long id, String code, String location) {
	}

	private record FieldOwner(Long supplierId) {
	}

	private final JdbcClient jdbc;

	SupplierDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean supplierExists(long id) {
		return jdbc.sql("select count(*) from suppliers where id = :id").param("id", id).query(Long.class).single() > 0;
	}

	public boolean fieldExists(long id) {
		return jdbc.sql("select count(*) from supplier_fields where id = :id").param("id", id)
			.query(Long.class).single() > 0;
	}

	public Map<Long, Supplier> suppliers(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, name, code from suppliers where id in (:ids)").param("ids", present)
			.query(Supplier.class).list().stream().collect(Collectors.toMap(Supplier::id, Function.identity()));
	}

	public Map<Long, Field> fields(Collection<Long> ids) {
		List<Long> present = ValidationErrors.present(ids);
		if (present.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, code, location from supplier_fields where id in (:ids)").param("ids", present)
			.query(Field.class).list().stream().collect(Collectors.toMap(Field::id, Function.identity()));
	}

	/** Tarlanin tedarikcisi. */
	public Optional<Long> fieldOwner(long fieldId) {
		return jdbc.sql("select supplier_id from supplier_fields where id = :id").param("id", fieldId)
			.query(FieldOwner.class).optional().map(FieldOwner::supplierId);
	}

}
