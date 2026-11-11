package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** On dinlendirme tankinin bosaltilmasi: hedef son tanklar ve her birine cekilen miktar (tankin kapasite biriminde). */
record EmptyRequest(
	@NotEmpty(message = MessageKeys.REQUIRED)
	List<@Valid Target> targets,

	String note) {

	EmptyRequest {
		note = Texts.trim(note);
	}

	record Target(
		@NotNull(message = MessageKeys.REQUIRED)
		Long storageUnitId,

		@NotNull(message = MessageKeys.REQUIRED)
		@DecimalMin(value = "0", inclusive = false, message = MessageKeys.GT)
		BigDecimal quantity) {
	}

}
