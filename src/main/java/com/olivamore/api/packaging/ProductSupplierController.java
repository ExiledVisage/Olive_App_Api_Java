package com.olivamore.api.packaging;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code ProductSupplierController} + {@code ProductSupplierService}: ambalaj ve satin alinan malzemeyi
 * getiren firmalar (zeytin tedarikcisi {@code suppliers} ayri, catalog modulunde).
 */
@RestController
class ProductSupplierController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");
	private static final String LOTS_COUNT = "(select count(*) from packaging_lots"
		+ " where product_suppliers.id = packaging_lots.product_supplier_id) as lots_count";

	private final Db db;
	private final LaravelValidator validator;
	private final PackagingLots lots;

	ProductSupplierController(Db db, LaravelValidator validator, PackagingLots lots) {
		this.db = db;
		this.validator = validator;
		this.lots = lots;
	}

	/** {@code ?search=} ad veya kod; {@code ?is_active=}; ada gore. */
	@GetMapping("/api/product-suppliers")
	@RequiresPermission("product_supplier.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		StringBuilder sql = new StringBuilder("select product_suppliers.*, " + LOTS_COUNT + " from product_suppliers");
		List<String> where = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			String value = request.getParameter("is_active");
			where.add("is_active = ?");
			args.add(value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT)));
		}
		String search = request.getParameter("search") == null ? "" : request.getParameter("search").trim();
		if (!search.isEmpty()) {
			where.add("(name::text ilike ? or code::text ilike ?)");
			args.add("%" + search + "%");
			args.add("%" + search + "%");
		}
		if (!where.isEmpty()) {
			sql.append(" where ").append(String.join(" and ", where));
		}
		sql.append(" order by name asc");
		return new ListResponse.Plain<>(db.select(sql.toString(), args.toArray()).stream().map(s -> resource(s, null)).toList());
	}

	@GetMapping("/api/product-suppliers/{productSupplier}")
	@RequiresPermission("product_supplier.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "productSupplier", table = "product_suppliers", model = "ProductSupplier") Row supplier) {
		return DataResponse.of(byId(supplier.id()));
	}

	/** 201; {@code is_active} verilmezse true. */
	@PostMapping("/api/product-suppliers")
	@RequiresPermission("product_supplier.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input) {
		ValidatedInput v = validator.validate(input, rules("required", "unique:product_suppliers,code"));
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		if (data.get("is_active") == null) {
			data.put("is_active", true);
		}
		return DataResponse.created(byId(db.insert("product_suppliers", data)));
	}

	@PatchMapping("/api/product-suppliers/{productSupplier}")
	@RequiresPermission("product_supplier.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(
		@Bound(value = "productSupplier", table = "product_suppliers", model = "ProductSupplier") Row supplier, RequestInput input) {
		ValidatedInput v = validator.validate(input, rules("sometimes", "unique:product_suppliers,code," + supplier.id()));
		db.update("product_suppliers", supplier.id(), v.all());
		return DataResponse.of(byId(supplier.id()));
	}

	/** Lotu olan tedarikci silinmez; pasife alinir. */
	@DeleteMapping("/api/product-suppliers/{productSupplier}")
	@RequiresPermission("product_supplier.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "productSupplier", table = "product_suppliers", model = "ProductSupplier") Row supplier) {
		DeletionGuard.of("'" + supplier.string("name") + "' tedarikçisi")
			.usedBy("ambalaj lotu", db.count("select count(*) from packaging_lots where product_supplier_id = ?", supplier.id()))
			.check("Pasife alabilirsiniz.");
		db.delete("product_suppliers", supplier.id());
		return Map.of("message", "Ürün tedarikçisi silindi.");
	}

	private static Rules rules(String presence, String unique) {
		return Rules.of()
			.field("name", presence + "|string|max:255")
			.field("code", presence + "|string|max:50|" + unique)
			.field("phone", "nullable|string|max:50")
			.field("email", "nullable|email|max:255")
			.field("address", "nullable|string")
			.field("note", "nullable|string")
			.field("is_active", "sometimes|boolean");
	}

	/** {@code getById}: lot sayisi ve lotlar (kalemiyle, yeniden eskiye). */
	private Map<String, Object> byId(long id) {
		Row supplier = db.first("select product_suppliers.*, " + LOTS_COUNT + " from product_suppliers where id = ?", id).orElseThrow();
		List<Row> supplierLots = db.select("select * from packaging_lots where product_supplier_id = ?"
			+ " order by received_at desc, id desc", id);
		return resource(supplier, lots.resources(supplierLots, true, false, false));
	}

	/** ProductSupplierResource; {@code lots} yalnizca detayda yuklu. */
	private static Map<String, Object> resource(Row s, List<Map<String, Object>> lots) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", s.get("id"));
		r.put("name", s.get("name"));
		r.put("code", s.get("code"));
		r.put("phone", s.get("phone"));
		r.put("email", s.get("email"));
		r.put("address", s.get("address"));
		r.put("note", s.get("note"));
		r.put("is_active", Casts.bool(s.get("is_active")));
		r.put("lot_count", s.get("lots_count"));
		if (lots != null) {
			r.put("lots", lots);
		}
		r.put("created_at", s.get("created_at"));
		r.put("updated_at", s.get("updated_at"));
		return r;
	}

}
