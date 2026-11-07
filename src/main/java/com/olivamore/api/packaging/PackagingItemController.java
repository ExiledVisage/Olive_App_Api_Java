package com.olivamore.api.packaging;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Laravel {@code PackagingItemController} + {@code PackagingItemService}: ambalaj kalemleri (sise, kapak, etiket).
 * Eldeki adet ({@code current_stock}) buradan yazilmaz; lotlarin kalan toplami.
 */
@RestController
class PackagingItemController {

	private static final Set<String> TRUE_VALUES = Set.of("1", "true", "on", "yes");
	private static final String COUNTS = "(select count(*) from packaging_lots where packaging_items.id = packaging_lots.packaging_item_id) as lots_count,"
		+ " (select count(*) from packaging_lots where packaging_items.id = packaging_lots.packaging_item_id and status = 'active') as active_lot_count";

	private final Db db;
	private final LaravelValidator validator;
	private final PackagingLots lots;

	PackagingItemController(Db db, LaravelValidator validator, PackagingLots lots) {
		this.db = db;
		this.validator = validator;
		this.lots = lots;
	}

	/** {@code ?is_active} (istekte varsa filter_var BOOLEAN); en yeni once ({@code latest()}). */
	@GetMapping("/api/packaging-items")
	@RequiresPermission("packaging_item.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		StringBuilder sql = new StringBuilder("select packaging_items.*, " + COUNTS + " from packaging_items");
		List<Object> args = new ArrayList<>();
		if (InputNormalizationFilter.wasSent(request, "is_active")) {
			String value = request.getParameter("is_active");
			sql.append(" where is_active = ?");
			args.add(value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT)));
		}
		sql.append(" order by created_at desc");
		return new ListResponse.Plain<>(db.select(sql.toString(), args.toArray()).stream().map(PackagingItemController::resource).toList());
	}

	@GetMapping("/api/packaging-items/{packagingItem}")
	@RequiresPermission("packaging_item.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item) {
		return DataResponse.of(byId(item.id()));
	}

	/**
	 * Yeni kalem (201). {@code current_stock} verilirse sutuna degil devir lotu olarak yazilir
	 * ({@code DEVIR-2026-0018}).
	 */
	@PostMapping("/api/packaging-items")
	@RequiresPermission("packaging_item.manage")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "required|string|max:255")
			.field("type", "required|in:BOTTLE,CAP,LABEL")
			.field("current_stock", "nullable|integer|min:0")
			.field("is_active", "nullable|boolean"));
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		long opening = Php.intval(data.remove("current_stock"));
		if (data.get("is_active") == null) {
			data.put("is_active", true);
		}
		data.put("current_stock", 0);
		long id = db.insert("packaging_items", data);
		if (opening > 0) {
			lots.receive(id, String.format("DEVIR-%d-%04d", Timestamps.now().getYear(), id), opening, null, null,
				"Kalem tanımlanırken girilen devir stoğu.", user.id());
		}
		return DataResponse.created(byId(id));
	}

	/** Kalem kunyesi; {@code current_stock} dogrulanir ama yazilmaz. */
	@RequestMapping(path = "/api/packaging-items/{packagingItem}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	@RequiresPermission("packaging_item.manage")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item,
		RequestInput input) {
		ValidatedInput v = validator.validate(input, Rules.of()
			.field("name", "sometimes|string|max:255")
			.field("type", "sometimes|in:BOTTLE,CAP,LABEL")
			.field("current_stock", "sometimes|integer|min:0")
			.field("is_active", "sometimes|boolean"));
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		data.remove("current_stock");
		db.update("packaging_items", item.id(), data);
		return DataResponse.of(byId(item.id()));
	}

	@DeleteMapping("/api/packaging-items/{packagingItem}")
	@RequiresPermission("packaging_item.manage")
	@Transactional
	Map<String, String> destroy(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item) {
		if (db.count("select count(*) from finished_product_components where packaging_item_id = ?", item.id()) > 0) {
			throw new BusinessRuleException("Bu ambalaj kalemi bir ürün bileşeninde kullanıldığı için silinemez.");
		}
		if (db.count("select count(*) from packaging_lots where packaging_item_id = ?", item.id()) > 0) {
			throw new BusinessRuleException("Bu ambalaj kaleminin lot kayıtları var; silinemez. Pasife alabilirsiniz.");
		}
		db.delete("packaging_items", item.id());
		return Map.of("message", "Ambalaj kalemi başarıyla silindi.");
	}

	/** {@code getById}: lot sayilari yuklu. */
	private Map<String, Object> byId(long id) {
		return resource(db.first("select packaging_items.*, " + COUNTS + " from packaging_items where id = ?", id).orElseThrow());
	}

	/** PackagingItemResource ({@code lots} hicbir uc tarafindan yuklenmiyor). */
	private static Map<String, Object> resource(Row i) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", i.get("id"));
		r.put("name", i.get("name"));
		r.put("type", i.get("type"));
		r.put("current_stock", i.get("current_stock"));
		r.put("lot_count", i.get("lots_count"));
		r.put("active_lot_count", Php.intval(i.get("active_lot_count")));
		r.put("is_active", Casts.bool(i.get("is_active")));
		r.put("created_at", i.get("created_at"));
		r.put("updated_at", i.get("updated_at"));
		return r;
	}

}
