package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.NotEmpty;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Proses alan degerleri; alan ve degerin tipi serviste (alanin veri tipine gore) dogrulanir. */
record SyncFieldValuesRequest(
	@NotEmpty(message = MessageKeys.REQUIRED)
	List<Item> fields) {

	record Item(Long processTypeFieldId, JsonNode value) {
	}

}
