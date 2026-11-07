package com.olivamore.api.process.processtypefield;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code StoreProcessTypeFieldRequest}, {@code UpdateProcessTypeFieldRequest}.
 * <p>
 * Laravel'de unique kurali route('processType') ile (null): ayni proseste tekrar eden anahtar veritabani
 * kisitina takilip 500 veriyordu; burada proses tipine gore 422.
 */
final class ProcessTypeFieldRequests {

	static final String FIELD_TYPES = "in:text,decimal,integer,boolean,datetime,json";

	private ProcessTypeFieldRequests() {
	}

	static Rules store(long processTypeId) {
		return rules(true, processTypeId, null);
	}

	static Rules update(long processTypeId, long fieldId) {
		return rules(false, processTypeId, fieldId);
	}

	private static Rules rules(boolean store, long processTypeId, Long fieldId) {
		String presence = store ? "required" : "sometimes";
		return Rules.of()
			.field("field_key", presence + "|string|max:255|unique:process_type_fields,field_key,"
				+ (fieldId == null ? "NULL" : fieldId) + ",id,process_type_id," + processTypeId)
			.field("label", presence + "|string|max:255")
			.field("data_type", presence + "|string|" + FIELD_TYPES)
			.field("is_required", (store ? "nullable" : "sometimes") + "|boolean")
			.field("unit", "nullable|string|max:50")
			.field("sort_order", (store ? "nullable" : "sometimes") + "|integer|min:0")
			.field("is_active", (store ? "nullable" : "sometimes") + "|boolean")
			.field("options", "nullable|array")
			.field("default_value", "nullable|string");
	}

}
