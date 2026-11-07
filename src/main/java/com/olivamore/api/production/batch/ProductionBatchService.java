package com.olivamore.api.production.batch;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.php.PhpDates;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.LaravelPaginator;
import com.olivamore.api.shared.web.ListResponse;
import com.olivamore.api.shared.web.Listing;
import com.olivamore.api.shared.web.RequestInput;
import com.olivamore.api.shared.web.binding.Bound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Laravel {@code ProductionBatchService}: partiler. Girdiler hattin kabul ettigi kategori/cesitlerden, ayni "type"ta
 * olmali; ciktilar hattin cikti urunlerinden girdinin cesidine gore cozulur; hattin her asamasi icin bekleyen bir
 * proses acilir.
 */
@Service
class ProductionBatchService {

	private static final String TABLE = "production_batches";

	private static final String STATUSES = "in:pending,in_progress,completed,cancelled";

	private final Db db;

	ProductionBatchService(Db db) {
		this.db = db;
	}

	/** Varis gunu suzgeci (Y-m-d, dahil): where kosulu ve parametreleri. */
	record Filter(String where, List<Object> args) {
	}

	Filter filter(ValidatedInput filters) {
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		StringBuilder where = new StringBuilder();
		if (filters.get("received_from") != null) {
			conditions.add("received_at::date >= ?");
			args.add(filters.get("received_from"));
		}
		if (filters.get("received_to") != null) {
			conditions.add("received_at::date <= ?");
			args.add(filters.get("received_to"));
		}
		if (!conditions.isEmpty()) {
			where.append(" where ").append(String.join(" and ", conditions));
		}
		return new Filter(where.toString(), args);
	}

	/** En yeni once. */
	List<Row> all(Filter f) {
		return db.select("select * from production_batches" + f.where() + " order by created_at desc", f.args().toArray());
	}

	long count(Filter f) {
		return db.count("select count(*) as aggregate from production_batches" + f.where(), f.args().toArray());
	}

	List<Row> page(Filter f, int page, int perPage) {
		return db.select("select * from production_batches" + f.where() + " order by created_at desc"
			+ " limit " + perPage + " offset " + (long) (page - 1) * perPage, f.args().toArray());
	}

	/** Gun -> parti sayisi (varis gunu). */
	Map<String, Object> calendar(Object from, Object to) {
		Map<String, Object> days = new LinkedHashMap<>();
		for (Row r : db.select("select DATE(received_at) as day, COUNT(*) as total from production_batches"
			+ " where received_at is not null and received_at::date >= ? and received_at::date <= ? group by day order by day asc",
			from, to)) {
			days.put(r.get("day").toString(), Php.intval(r.get("total")));
		}
		return days;
	}

	Row byId(long id) {
		return db.find(TABLE, id).orElseThrow();
	}

	/** @return yeni parti id */
	long create(ValidatedInput v, CurrentUser user) {
		// collect(...)->filter()->map(int): bos (0) degerler atilir
		List<Long> inputIds = new ArrayList<>();
		for (Object id : list(v.get("input_production_product_type_ids"))) {
			if (truthy(id)) {
				inputIds.add(Php.intval(id));
			}
		}
		if (inputIds.isEmpty()) {
			throw new BusinessRuleException("En az bir giriş ürün tipi seçilmelidir.");
		}
		List<Row> found = db.select("select * from production_product_types where id in (" + Relations.inList(inputIds) + ")");
		if (found.size() != inputIds.size()) {
			throw new BusinessRuleException("Seçilen giriş ürün tiplerinden biri bulunamadı.");
		}
		List<Row> inputTypes = new ArrayList<>(found);
		inputTypes.sort(Comparator.comparingInt(t -> inputIds.indexOf(t.id())));

		Row line = db.find("production_lines", Php.intval(v.get("production_line_id")))
			.orElseThrow(() -> new ModelNotFoundException("ProductionLine", v.get("production_line_id")));
		assertInputProductsAllowed(line, inputTypes);
		String resolvedType = sharedInputType(inputTypes);
		List<Row> outputTypes = outputTypesForLine(line, resolvedType);

		Long supplierId = v.get("supplier_id") == null ? null : Php.intval(v.get("supplier_id"));
		Long fieldId = v.get("supplier_field_id") == null ? null : Php.intval(v.get("supplier_field_id"));
		assertFieldBelongsToSupplier(supplierId, fieldId);

		Map<String, Object> values = new LinkedHashMap<>();
		values.put("batch_code", v.get("batch_code"));
		values.put("production_line_id", line.id());
		values.put("supplier_id", supplierId);
		values.put("supplier_field_id", fieldId);
		values.put("created_by_user_id", user.id());
		values.put("status", v.get("status") == null ? "pending" : v.get("status"));
		values.put("quantity_kg", v.get("quantity_kg"));
		values.put("received_at", v.get("received_at") == null ? Timestamps.now() : date(v.get("received_at")));
		values.put("started_at", date(v.get("started_at")));
		values.put("completed_at", date(v.get("completed_at")));
		values.put("note", v.get("note"));
		long id = db.insert(TABLE, values);

		for (int i = 0; i < inputTypes.size(); i++) {
			db.insert("production_batch_input_production_product_types", Map.of(
				"production_product_type_id", inputTypes.get(i).id(), "sort_order", i, "production_batch_id", id));
		}
		for (int i = 0; i < outputTypes.size(); i++) {
			db.insert("production_batch_output_production_product_types", Map.of(
				"production_product_type_id", outputTypes.get(i).id(), "sort_order", i, "production_batch_id", id));
		}
		for (Row stage : db.whereIn("production_stages", "production_line_id", List.of(line.id()), "stage_order asc")) {
			Map<String, Object> process = new LinkedHashMap<>();
			process.put("production_stage_id", stage.id());
			process.put("status", "waiting");
			process.put("operator_id", null);
			process.put("started_at", null);
			process.put("completed_at", null);
			process.put("note", null);
			process.put("production_batch_id", id);
			db.insert("batch_processes", process);
		}
		return id;
	}

	/** Tedarikci degisip tarla verilmediyse eski tarla duser. Yalnizca gonderilen alanlar. */
	void update(Row batch, ValidatedInput v) {
		Map<String, Object> payload = new LinkedHashMap<>();
		if (v.has("supplier_id") || v.has("supplier_field_id")) {
			Long current = Relations.key(batch.get("supplier_id"));
			Long supplierId = v.has("supplier_id") ? (v.get("supplier_id") == null ? null : Php.intval(v.get("supplier_id"))) : current;
			Long fieldId = v.has("supplier_field_id")
				? (v.get("supplier_field_id") == null ? null : Php.intval(v.get("supplier_field_id")))
				: (Objects.equals(supplierId, current) ? Relations.key(batch.get("supplier_field_id")) : null);
			assertFieldBelongsToSupplier(supplierId, fieldId);
			payload.put("supplier_id", supplierId);
			payload.put("supplier_field_id", fieldId);
		}
		for (String key : List.of("batch_code", "status", "quantity_kg", "received_at", "started_at", "completed_at", "note")) {
			if (v.has(key)) {
				payload.put(key, key.endsWith("_at") ? date(v.get(key)) : v.get(key));
			}
		}
		if (!payload.isEmpty()) {
			db.update(TABLE, batch.id(), payload);
		}
	}

	/** Yalnizca hic baslamamis parti silinir (proses, materyal ya da stok hareketi varsa 422). */
	void delete(Row batch) {
		List<Long> processIds = db.select("select id from batch_processes where batch_processes.production_batch_id = ?"
			+ " and batch_processes.production_batch_id is not null", batch.id()).stream().map(Row::id).toList();
		DeletionGuard.of("'" + Php.strval(batch.get("batch_code")) + "' partisi")
			.usedBy("başlamış proses", db.count("select count(*) as aggregate from batch_processes where batch_processes.production_batch_id = ?"
				+ " and batch_processes.production_batch_id is not null and status != ?", batch.id(), "waiting"))
			.usedBy("materyal", db.count("select count(*) as aggregate from materials where production_batch_id = ?", batch.id()))
			.usedBy("stok hareketi", processIds.isEmpty() ? 0
				: db.count("select count(*) as aggregate from storage_movements where batch_process_id in (" + Relations.inList(processIds) + ")"))
			.check("Başlamış bir parti silinmez; iptal etmek için durumunu cancelled yapın.");
		db.delete(TABLE, batch.id());
	}

	// ---- kurallar ----------------------------------------------------------

	/** Hat girdiyi kategoriyle (her zaman) ve istege bagli cesitle sinirlar. */
	private void assertInputProductsAllowed(Row line, List<Row> inputTypes) {
		Map<Long, List<Long>> allowed = new LinkedHashMap<>();
		for (Row a : db.whereIn("production_line_input_production_products", "production_line_id", List.of(line.id()), "sort_order asc")) {
			List<Long> types = allowed.computeIfAbsent(Php.intval(a.get("production_product_id")), k -> new ArrayList<>());
			if (a.get("production_product_type_id") != null) {
				types.add(Php.intval(a.get("production_product_type_id")));
			}
		}
		for (Row type : inputTypes) {
			long category = Php.intval(type.get("production_product_id"));
			if (!allowed.containsKey(category)) {
				throw new BusinessRuleException("Seçilen giriş ürün tipi (" + Php.strval(type.get("name")) + ") bu production line için uygun değil.");
			}
			List<Long> types = allowed.get(category);
			if (!types.isEmpty() && !types.contains(type.id())) {
				List<String> names = db.select("select name from production_product_types where id in (" + Relations.inList(types) + ")")
					.stream().map(r -> Php.strval(r.get("name"))).toList();
				throw new BusinessRuleException("Bu üretim hattı yalnızca " + String.join(", ", names) + " kabul ediyor; "
					+ Php.strval(type.get("name")) + " ile parti açılamaz.");
			}
		}
	}

	/** Girdilerin ortak "type" degeri (kirpilmis; null ile "" PHP'deki gibi ayni sayilir). */
	private static String sharedInputType(List<Row> inputTypes) {
		LinkedHashSet<String> types = new LinkedHashSet<>();
		String first = null;
		boolean seen = false;
		for (Row t : inputTypes) {
			String type = t.get("type") == null ? null : Php.strval(t.get("type")).strip();
			types.add(type == null ? "" : type);
			if (!seen) {
				first = type;
				seen = true;
			}
		}
		if (types.size() > 1) {
			throw new BusinessRuleException("Farklı type değerine sahip giriş ürün tipleri aynı partide şu an desteklenmiyor.");
		}
		return first;
	}

	/** Hattin ciktilari (id sirasiyla): cesit kilitliyse o, degilse girdinin cesidi, yoksa cesitsiz tip. */
	private List<Row> outputTypesForLine(Row line, String resolvedType) {
		List<Row> outputs = new ArrayList<>(db.whereIn("production_line_output_production_products", "production_line_id",
			List.of(line.id()), "sort_order asc"));
		outputs.sort(Comparator.comparingLong(Row::id));
		List<Row> out = new ArrayList<>();
		for (Row o : outputs) {
			if (o.get("production_product_type_id") != null) {
				out.add(db.find("production_product_types", Php.intval(o.get("production_product_type_id")))
					.orElseThrow(() -> new ModelNotFoundException("ProductionProductType", o.get("production_product_type_id"))));
				continue;
			}
			Row matched = null;
			if (resolvedType != null && !resolvedType.isEmpty()) {
				matched = db.first("select * from production_product_types where production_product_id = ? and type = ? limit 1",
					o.get("production_product_id"), resolvedType).orElse(null);
			}
			if (matched == null) {
				matched = db.first("select * from production_product_types where production_product_id = ? and type is null limit 1",
					o.get("production_product_id")).orElse(null);
			}
			if (matched == null) {
				Row product = db.find("production_products", Php.intval(o.get("production_product_id"))).orElse(null);
				String name = product == null || product.get("name") == null ? "çıkış ürün tipi" : Php.strval(product.get("name"));
				throw new BusinessRuleException(name + " için uygun productionProductType bulunamadı.");
			}
			out.add(matched);
		}
		return out;
	}

	/** SupplierService::assertFieldBelongsToSupplier */
	private void assertFieldBelongsToSupplier(Long supplierId, Long fieldId) {
		if (fieldId == null) {
			return;
		}
		if (supplierId == null) {
			throw new BusinessRuleException("Tarla seçildiyse tedarikçi de seçilmelidir.");
		}
		Row field = db.find("supplier_fields", fieldId).orElse(null);
		if (field == null || Php.intval(field.get("supplier_id")) != supplierId) {
			throw new BusinessRuleException("Seçilen tarla bu tedarikçiye ait değil.");
		}
	}

	/** datetime cast'inin yazdigi deger (dogrulanmis {@code date}). */
	private static LocalDateTime date(Object value) {
		return value == null ? null : PhpDates.parse(Php.strval(value)).orElseThrow();
	}

	private static List<?> list(Object value) {
		return value instanceof List<?> l ? l : value instanceof Map<?, ?> m ? new ArrayList<>(m.values()) : List.of();
	}

	private static boolean truthy(Object value) {
		return value != null && !Boolean.FALSE.equals(value) && !(value instanceof String s && (s.isEmpty() || s.equals("0")))
			&& !(value instanceof Number n && n.doubleValue() == 0);
	}

}
