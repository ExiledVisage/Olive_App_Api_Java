package com.olivamore.api.identity.role;

import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Izin listesi gelmezse rolun butun izinleri kaldirilir. */
record UpdateRoleRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	List<String> permissions) {

	UpdateRoleRequest {
		name = Texts.trim(name);
	}

}
