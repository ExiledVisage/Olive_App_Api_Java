package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.Texts;
import tools.jackson.databind.JsonNode;

/** Makine adimi duzeltmesi; {@code null} gelen alan degismez, {@code values} verilirse tamami degisir. */
record CorrectMachineRunRequest(
	Long machinePartId,

	Patch<String> startedAt,

	Patch<String> finishedAt,

	Patch<String> note,

	JsonNode values) {

	CorrectMachineRunRequest {
		note = Texts.trim(note);
	}

}
