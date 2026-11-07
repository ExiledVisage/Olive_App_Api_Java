package com.olivamore.api.packaging.lot;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.validation.LaravelValidator;
import com.olivamore.api.shared.validation.ValidatedInput;
import com.olivamore.api.shared.web.DataResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.olivamore.api.shared.php.PhpDates;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

/** Laravel {@code PackagingLotController}: lotlar, mal girisi, kunye duzeltmesi, sayim duzeltmesi, silme. */
@RestController
class PackagingLotController {

	private final LaravelValidator validator;
	private final PackagingLotService lots;
	private final PackagingLotResource resource;

	PackagingLotController(LaravelValidator validator, PackagingLotService lots, PackagingLotResource resource) {
		this.validator = validator;
		this.lots = lots;
		this.resource = resource;
	}

	// ---- okuma (packaging_lot.view) ------------------------------------------

	/** {@code ?packaging_item_id= ?product_supplier_id= ?status= ?search=}; kalem ve tedarikci yuklu. */
	@GetMapping("/api/packaging-lots")
	@RequiresPermission("packaging_lot.view")
	ListResponse<Map<String, Object>> index(HttpServletRequest request) {
		return new ListResponse.Plain<>(resource.of(lots.list(integer(request, "packaging_item_id"), integer(request, "product_supplier_id"),
			request.getParameter("status"), request.getParameter("search")), true, true, false));
	}

	@GetMapping("/api/packaging-items/{packagingItem}/lots")
	@RequiresPermission("packaging_lot.view")
	ListResponse<Map<String, Object>> indexForItem(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item) {
		return new ListResponse.Plain<>(resource.of(lots.list(item.id(), null, null, null), true, true, false));
	}

	@GetMapping("/api/packaging-lots/{packagingLot}")
	@RequiresPermission("packaging_lot.view")
	DataResponse<Map<String, Object>> show(@Bound(value = "packagingLot", table = "packaging_lots", model = "PackagingLot") Row lot) {
		return DataResponse.of(resource.detail(lot.id()));
	}

	// ---- mal girisi ve kunye (packaging_lot.receive) -------------------------

	/** Mal girisi: yeni lot (201). */
	@PostMapping("/api/packaging-items/{packagingItem}/lots")
	@RequiresPermission("packaging_lot.receive")
	@Transactional
	ResponseEntity<DataResponse<Map<String, Object>>> store(@Bound(value = "packagingItem", table = "packaging_items", model = "PackagingItem") Row item,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, PackagingLotRequests.store(item.id()));
		long lotId = lots.receive(item.id(), v.string("lot_code"), Php.intval(v.get("quantity")), date(v.get("received_at")),
			v.get("product_supplier_id"), v.get("note"), user.id());
		return DataResponse.created(resource.detail(lotId));
	}

	/** Kunye duzeltmesi: lot numarasi, tedarikci, giris zamani, not. */
	@PatchMapping("/api/packaging-lots/{packagingLot}")
	@RequiresPermission("packaging_lot.receive")
	@Transactional
	DataResponse<Map<String, Object>> update(@Bound(value = "packagingLot", table = "packaging_lots", model = "PackagingLot") Row lot, RequestInput input) {
		ValidatedInput v = validator.validate(input, PackagingLotRequests.update(lot));
		Map<String, Object> data = new LinkedHashMap<>(v.all());
		if (data.containsKey("received_at")) {
			data.put("received_at", date(data.get("received_at")));
		}
		lots.update(lot, data);
		return DataResponse.of(resource.detail(lot.id()));
	}

	@DeleteMapping("/api/packaging-lots/{packagingLot}")
	@RequiresPermission("packaging_lot.receive")
	@Transactional
	Map<String, String> destroy(@Bound(value = "packagingLot", table = "packaging_lots", model = "PackagingLot") Row lot) {
		lots.delete(lot);
		return Map.of("message", "Ambalaj lotu silindi.");
	}

	// ---- sayim duzeltmesi (packaging_lot.adjustment) -------------------------

	/** Sayim farki, fire ya da iade. */
	@PostMapping("/api/packaging-lots/{packagingLot}/adjustment")
	@RequiresPermission("packaging_lot.adjustment")
	@Transactional
	DataResponse<Map<String, Object>> adjust(@Bound(value = "packagingLot", table = "packaging_lots", model = "PackagingLot") Row lot,
		RequestInput input, CurrentUser user) {
		ValidatedInput v = validator.validate(input, PackagingLotRequests.adjust());
		lots.adjust(lot.id(), Php.intval(v.get("delta")), v.has("reason") ? Php.strval(v.get("reason")) : null, v.get("note"), user.id());
		return DataResponse.of(resource.detail(lot.id()));
	}

	// ---- yardimcilar -----------------------------------------------------------

	/** {@code $request->integer($key) ?: null} */
	private static Long integer(HttpServletRequest request, String key) {
		String value = request.getParameter(key);
		long n = value == null ? 0 : Php.intval(value);
		return n == 0 ? null : n;
	}

	/** datetime cast'inin yazdigi deger ({@code Carbon::parse}, saat dilimi cevrilmez). */
	private static LocalDateTime date(Object value) {
		return value == null ? null : PhpDates.parse(Php.strval(value)).orElseThrow();
	}

}
