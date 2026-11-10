package com.olivamore.api.production.batchprocess;

import com.olivamore.api.shared.validation.LaravelMessages;
import jakarta.validation.constraints.Size;

record ApproveBatchProcessRequest(
	@Size(max = 2000, message = LaravelMessages.MAX_2000)
	String note) {
}
