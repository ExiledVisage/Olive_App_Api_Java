package com.olivamore.api.production.batch;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Teslimattan grup: {@code quantity_kg} ya da {@code all_remaining} (ayrilmamis zeytinin hepsi). {@code start_stage_id}
 * teslimatta yapilmis makine oncesi asamalardan biri (grup o asamadan baslar, oncekiler teslimatta yapildi sayilir);
 * verilmezse grup kaldigi yerden devam eder.
 */
record CreateBatchGroupRequest(
	@DecimalMin(value = "0", inclusive = false, message = MessageKeys.MIN)
	BigDecimal quantityKg,

	Boolean allRemaining,

	Long startStageId,

	String note) {

	CreateBatchGroupRequest {
		note = Texts.trim(note);
	}

}
