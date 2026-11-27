package com.olivamore.api.integration.provider;

import java.util.List;

/**
 * Pazar yerinin kargo etiketi (docs/INTEGRATIONS.md): kargo firmasi, takip numarasi ve etiket(ler). Trendyol ve
 * Hepsiburada etiketi termal yazici dilinde (ZPL) verir; cok kolili gonderide birden fazla etiket olur.
 *
 * @param format ZPL
 */
public record ShippingLabel(String cargoCompany, String trackingNumber, String format, List<String> labels) {
}
