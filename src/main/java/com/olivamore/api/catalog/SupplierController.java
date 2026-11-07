package com.olivamore.api.catalog;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.Rules;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.InputNormalizationFilter;
import com.olivamore.api.shared.web.ListResponse;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code SupplierController} + {@code SupplierService}: zeytin tedarikcileri ve tarlalari.
 * (Ambalaj tedarikcileri, {@code product-suppliers}, packaging modulunde.)
 */
@RestController
class SupplierController {

	static final String FIELD_NOT_OF_SUPPLIER = "Bu tarla bu tedarikçiye ait değil.";
	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");

	private final Db db;
	private final LaravelValidator validator;

	SupplierController(Db db, LaravelValidator validator) {
		this.db = db;
		this.validator = validator;
	}

	// ---- okuma (supplier.view) ---------------------------------------------

	/**
	 * {@code ?is_active}: istekte varsa (bos olsa bile) {@code filter_var(BOOLEAN)} ile filtre;
	 * {@code ?search}: ad, kod ya da tarla kodunda buyuk/kucuk harfe duyarsiz (whereLike -> ilike).
	 */
	@GetMapping("/api/suppliers")
	@RequiresPermission("supplier.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		StringBuilder sql = new StringBuilder("""
			select suppliers.*, (select count(*) from production_batches
			  where suppliers.id = production_batches.supplier_id) as production_batches_count
			from suppliers""");
		List<Object> args = new ArrayList<>();
		List<String> where = new ArrayList<>();
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			where.add("is_active = ?");
			args.add(filterBoolean(request.getParameter("is_active")));
		}
		String search = request.getParameter("search") == null ? "" : request.getParameter("search").trim();
		if (!search.isEmpty()) {
			String like = "%" + search + "%";
			where.add("(name::text ilike ? or code::text ilike ? or exists (select * from supplier_fields"
				+ " where suppliers.id = supplier_fields.supplier_id and code::text ilike ?))");
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (!where.isEmpty()) {
			sql.append(" where ").append(String.join(" and ", where));
		}
		sql.append(" order by name asc");
		return new ListResponse.Plain<>(suppliers(db.select(sql.toString(), args.toArray())));
	}

	@GetMapping("/api/suppliers/{supplier}")
	@RequiresPermission("supplier.view")
	DataResponse<Map<String, Object>> show(@Bound(table = "suppliers", model = "Supplier") Row supplier) {
		return DataResponse.of(byId(supplier));
	}

	/** Tedarikcinin partileri (en yeni once) ve her partinin kantar tartimi. */
	@GetMapping("/api/suppliers/{supplier}/deliveries")
	@RequiresPermission("supplier.view")
	Map<String, Object> deliveries(@Bound(table = "suppliers", model = "Supplier") Row supplier) {
		List<Map<String, Object>> deliveries = new SupplierDeliveries(db).forSupplier(supplier.id());
		BigDecimal total = BigDecimal.ZERO;
		for (Map<String, Object> d : deliveries) {
			total = total.add(floatval(d.get("weighed_quantity_kg")));
		}
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("delivery_count", deliveries.size());
		summary.put("total_weighed_kg", phpFloat(total.setScale(2, RoundingMode.HALF_UP)));
		summary.put("last_received_at", deliveries.isEmpty() ? null : deliveries.getFirst().get("received_at"));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("data", deliveries);
		body.put("summary", summary);
		return body;
	}

	// ---- yazma (supplier.manage) -------------------------------------------

	/** Cevap 201: olusan model (yalnizca verilen alanlar) + tarlalar ve parti sayisi. */
	@PostMapping("/api/suppliers")
	@RequiresPermission("supplier.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255")
			.field("code", "required|string|max:50|unique:suppliers,code")
			.field("phone", "nullable|string|max:50")
			.field("email", "nullable|email|max:255")
			.field("address", "nullable|string")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean")
			.field("fields", "sometimes|array")
			.field("fields.*.code", "required|string|max:50|distinct|unique:supplier_fields,code")
			.field("fields.*.location", "nullable|string|max:255")
			.field("fields.*.area_decare", "nullable|numeric|min:0")
			.field("fields.*.note", "nullable|string"));
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		Object fields = data.remove("fields");
		Row supplier = db.create("suppliers", data);
		for (Map<String, Object> field : CatalogWriteController.items(fields)) {
			Map<String, Object> values = new LinkedHashMap<>(field);
			values.put("supplier_id", supplier.id());
			db.insert("supplier_fields", values);
		}
		return DataResponse.created(byId(supplier));
	}

	@PatchMapping("/api/suppliers/{supplier}")
	@RequiresPermission("supplier.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(table = "suppliers", model = "Supplier") Row supplier, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("code", "sometimes|string|max:50|unique:suppliers,code," + supplier.id())
			.field("phone", "nullable|string|max:50")
			.field("email", "nullable|email|max:255")
			.field("address", "nullable|string")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean"));
		db.update("suppliers", supplier.id(), v.all());
		return DataResponse.of(byId(db.find("suppliers", supplier.id()).orElseThrow()));
	}

	@DeleteMapping("/api/suppliers/{supplier}")
	@RequiresPermission("supplier.manage")
	@Transactional
	Map<String, String> destroy(@Bound(table = "suppliers", model = "Supplier") Row supplier) {
		DeletionGuard.of("'" + supplier.string("name") + "' tedarikçisi")
			.usedBy("üretim partisi", db.count("select count(*) from production_batches where supplier_id = ?", supplier.id()))
			.check("Pasife alabilirsiniz.");
		db.delete("suppliers", supplier.id());
		return Map.of("message", "Tedarikçi silindi.");
	}

	@PostMapping("/api/suppliers/{supplier}/fields")
	@RequiresPermission("supplier.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> storeField(
		@Bound(table = "suppliers", model = "Supplier") Row supplier, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("code", "required|string|max:50|unique:supplier_fields,code")
			.field("location", "nullable|string|max:255")
			.field("area_decare", "nullable|numeric|min:0")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean"));
		Map<String, Object> values = new LinkedHashMap<>(v.all());
		values.put("supplier_id", supplier.id());
		return DataResponse.created(field(db.create("supplier_fields", values)));
	}

	/** Once validation (FormRequest), sonra "bu tedarikcinin mi" (abort 404). */
	@PatchMapping("/api/suppliers/{supplier}/fields/{field}")
	@RequiresPermission("supplier.manage")
	@Transactional
	DataResponse<Map<String, Object>> updateField(@Bound(table = "suppliers", model = "Supplier") Row supplier,
		@Bound(table = "supplier_fields", model = "SupplierField") Row field, RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("code", "sometimes|string|max:50|unique:supplier_fields,code," + field.id())
			.field("location", "nullable|string|max:255")
			.field("area_decare", "nullable|numeric|min:0")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean"));
		ensureFieldOf(supplier, field);
		db.update("supplier_fields", field.id(), v.all());
		return DataResponse.of(field(db.find("supplier_fields", field.id()).orElseThrow()));
	}

	@DeleteMapping("/api/suppliers/{supplier}/fields/{field}")
	@RequiresPermission("supplier.manage")
	@Transactional
	Map<String, String> destroyField(@Bound(table = "suppliers", model = "Supplier") Row supplier,
		@Bound(table = "supplier_fields", model = "SupplierField") Row field) {
		ensureFieldOf(supplier, field);
		DeletionGuard.of("'" + field.string("code") + "' tarlası")
			.usedBy("üretim partisi", db.count("select count(*) from production_batches where supplier_field_id = ?", field.id()))
			.check("Pasife alabilirsiniz.");
		db.delete("supplier_fields", field.id());
		return Map.of("message", "Tarla silindi.");
	}

	// ---- resource'lar ------------------------------------------------------

	/** {@code getById}: tarlalar + parti sayisi; tedarikci satiri oldugu gibi (create sonrasi verilen alanlar). */
	private Map<String, Object> byId(Row supplier) {
		long count = db.count("select count(*) from production_batches where supplier_id = ?", supplier.id());
		Map<String, Object> values = new LinkedHashMap<>(supplier.values());
		values.put("production_batches_count", count);
		return suppliers(List.of(new Row(values))).getFirst();
	}

	/** SupplierResource; tarlalar Laravel'in eager loading sorgusuyla (siralamasiz). */
	private List<Map<String, Object>> suppliers(List<Row> suppliers) {
		Map<Long, List<Map<String, Object>>> fields = new LinkedHashMap<>();
		for (Row f : db.whereIn("supplier_fields", "supplier_id", CatalogReads.ids(suppliers), null)) {
			fields.computeIfAbsent(f.longValue("supplier_id"), k -> new ArrayList<>()).add(field(f));
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row s : suppliers) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", s.get("id"));
			r.put("name", s.get("name"));
			r.put("code", s.get("code"));
			r.put("phone", s.get("phone"));
			r.put("email", s.get("email"));
			r.put("address", s.get("address"));
			r.put("note", s.get("note"));
			r.put("is_active", CatalogReads.bool(s.get("is_active")));
			r.put("delivery_count", s.get("production_batches_count"));
			r.put("fields", fields.getOrDefault(s.id(), List.of()));
			r.put("created_at", s.get("created_at"));
			r.put("updated_at", s.get("updated_at"));
			out.add(r);
		}
		return out;
	}

	/** SupplierFieldResource ({@code area_decare}: decimal:2). */
	private static Map<String, Object> field(Row f) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", f.get("id"));
		r.put("supplier_id", f.get("supplier_id"));
		r.put("code", f.get("code"));
		r.put("location", f.get("location"));
		Object area = f.get("area_decare");
		r.put("area_decare", area == null ? null : new BigDecimal(Php.strval(area).strip()).setScale(2, RoundingMode.HALF_UP).toPlainString());
		r.put("note", f.get("note"));
		r.put("is_active", CatalogReads.bool(f.get("is_active")));
		return r;
	}

	private static void ensureFieldOf(Row supplier, Row field) {
		if (field.longValue("supplier_id") == null || field.longValue("supplier_id") != supplier.id()) {
			throw new HttpStatusException(404, FIELD_NOT_OF_SUPPLIER);
		}
	}

	/** {@code filter_var($v, FILTER_VALIDATE_BOOLEAN)}: "1", "true", "on", "yes" -> true, gerisi false. */
	static boolean filterBoolean(String value) {
		return value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
	}

	/** PHP (float) */
	private static BigDecimal floatval(Object value) {
		if (value == null || value instanceof Boolean b && !b) {
			return BigDecimal.ZERO;
		}
		if (value instanceof Boolean) {
			return BigDecimal.ONE;
		}
		if (value instanceof Number n) {
			return new BigDecimal(n.toString());
		}
		try {
			return new BigDecimal(Php.strval(value).strip());
		}
		catch (NumberFormatException e) {
			return BigDecimal.valueOf(Php.intval(value));
		}
	}

	/** PHP json_encode float: tam sayiya esitse ".0" yazilmaz (64765.0 -> 64765). */
	static Object phpFloat(BigDecimal value) {
		BigDecimal stripped = value.stripTrailingZeros();
		return stripped.scale() <= 0 ? (Object) stripped.longValue() : stripped.doubleValue();
	}

}
