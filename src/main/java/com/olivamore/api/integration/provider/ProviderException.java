package com.olivamore.api.integration.provider;

import com.olivamore.api.shared.error.BusinessRuleException;

/**
 * Platformla konusulamadi; mesaj kullaniciya gosterilir (elle tetiklenen iste 422, senkron gecmisi ve baglantinin
 * son hatasi).
 */
public class ProviderException extends BusinessRuleException {

	public ProviderException(String message, Throwable cause) {
		super(message);
		initCause(cause);
	}

}
