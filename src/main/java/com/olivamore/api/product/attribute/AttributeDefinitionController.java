package com.olivamore.api.product.attribute;

import com.olivamore.api.identity.RequiresPermission;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Laravel {@code AttributeDefinitionController}: global attribute tanimlari (yalnizca liste; tanimlar
 * kategori eslemesi eklenirken olusur).
 */
@RestController
class AttributeDefinitionController {

	private final Db db;

	AttributeDefinitionController(Db db) {
		this.db = db;
	}

	@GetMapping("/api/attribute-definitions")
	@RequiresPermission("attribute_definition.view")
	ListResponse<Map<String, Object>> index() {
		return new ListResponse.Plain<>(db.select("select * from attribute_definitions order by label asc").stream()
			.map(AttributeDefinitionController::resource).toList());
	}

	/** AttributeDefinitionResource */
	private static Map<String, Object> resource(Row d) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("id", d.get("id"));
		r.put("attribute_key", d.get("attribute_key"));
		r.put("label", d.get("label"));
		r.put("data_type", d.get("data_type"));
		r.put("unit", d.get("unit"));
		r.put("description", d.get("description"));
		r.put("created_at", d.get("created_at"));
		r.put("updated_at", d.get("updated_at"));
		return r;
	}

}
