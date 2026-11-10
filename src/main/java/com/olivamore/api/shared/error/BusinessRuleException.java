package com.olivamore.api.shared.error;

/**
 * Bir is kuralinin ihlal edildigini bildirir (422). Servis katmani kullaniciya gosterilebilir her reddetme icin
 * bunu firlatir (tank kapasitesi yetmiyor, materyal bu tankta degil...). Mesaj cevabin {@code detail} alanidir.
 * <p>
 * Programlama hatalari bununla sarilmamali; onlar 500 olarak kalmali.
 */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}

}
