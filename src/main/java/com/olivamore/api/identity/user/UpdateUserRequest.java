package com.olivamore.api.identity.user;

import com.olivamore.api.shared.validation.Texts;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.validation.MessageKeys;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Sifre bossa degismez. {@code roles} gelmezse roller degismez, {@code null} gelirse hepsi kaldirilir. */
record UpdateUserRequest(
	@NotBlank(message = MessageKeys.REQUIRED)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String name,

	@NotBlank(message = MessageKeys.REQUIRED)
	@Email(message = MessageKeys.EMAIL)
	@Size(max = 255, message = MessageKeys.SIZE_MAX)
	String email,

	@Size(min = 8, message = MessageKeys.SIZE_MIN)
	String password,

	Patch<List<String>> roles) {

	UpdateUserRequest {
		name = Texts.trim(name);
		email = Texts.trim(email);
	}

}
