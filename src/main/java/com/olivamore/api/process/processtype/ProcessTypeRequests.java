package com.olivamore.api.process.processtype;

import com.olivamore.api.shared.validation.Rules;

/**
 * Laravel {@code StoreProcessTypeRequest}, {@code UpdateProcessTypeRequest}.
 * <p>
 * Laravel'de ignore() route('processType') ile (parametre adi process_type): kendi adiyla guncelleme 422;
 * burada gecer.
 */
final class ProcessTypeRequests {

	static final String CATEGORIES = "in:sourcing,weighing,sorting,transfer,measurement,quality,storage,machine,yield_analysis";

	private ProcessTypeRequests() {
	}

	static Rules store() {
		return Rules.of()
			.field("name", "required|string|max:255|unique:process_types,name")
			.field("description", "nullable|string")
			.field("category", "required|string|" + CATEGORIES)
			.field("is_active", "nullable|boolean");
	}

	static Rules update(long processTypeId) {
		return Rules.of()
			.field("name", "sometimes|string|max:255|unique:process_types,name," + processTypeId)
			.field("description", "nullable|string")
			.field("category", "sometimes|string|" + CATEGORIES)
			.field("is_active", "sometimes|boolean");
	}

}
