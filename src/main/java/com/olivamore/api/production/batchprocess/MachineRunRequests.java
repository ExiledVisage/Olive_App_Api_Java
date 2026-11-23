package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.validation.Texts;
import tools.jackson.databind.JsonNode;

/** Makine adimi baslatma ve bitirme istekleri. */
final class MachineRunRequests {

	private MachineRunRequests() {
	}

	/** Tipte tek secilebilir parca varsa bos birakilir. */
	record Start(Long machinePartId) {
	}

	/** Olcumler {@code field_key -> deger}; aralik disi deger kaydedilir, isaretlenir. */
	record Finish(JsonNode values, String note) {

		Finish {
			note = Texts.trim(note);
		}

	}

}
