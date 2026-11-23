package com.olivamore.api.machine;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record CreateMachineTypeFieldRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 50, message = MessageKeys.SIZE_MAX)
	@Pattern(regexp = "[a-z][a-z0-9_]*", message = MachineRequests.KEY_FORMAT)
	String fieldKey,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String label,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Pattern(regexp = "decimal|integer|text|boolean", message = MessageKeys.IN)
	String dataType,

	/** run (varsayilan): calismada girilir; machine: makine kaydinda durur. */
	@Pattern(regexp = "run|machine", message = MessageKeys.IN)
	String scope,

	@Size(max = 20, message = MessageKeys.SIZE_MAX)
	String unit,

	Boolean isRequired,

	@Min(value = 1, message = MessageKeys.MIN)
	Integer sortOrder,

	BigDecimal minValue,

	BigDecimal maxValue,

	Boolean isActive) {

	CreateMachineTypeFieldRequest {
		fieldKey = Texts.trim(fieldKey);
		label = Texts.trim(label);
		dataType = Texts.trim(dataType);
		scope = Texts.trim(scope);
		unit = Texts.trim(unit);
	}

}
