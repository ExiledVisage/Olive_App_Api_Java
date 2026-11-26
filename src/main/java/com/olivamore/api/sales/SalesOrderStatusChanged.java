package com.olivamore.api.sales;

/**
 * Siparisin durumu degisti; durum gecisiyle ayni islemde yayinlanir. Entegrasyonlar islem tamamlandiktan sonra
 * dinler (ornegin pazar yerine "hazirlaniyor" bildirimi); dinleyicinin hatasi durum degisikligini geri almaz.
 *
 * @param from onceki durum ("draft", "confirmed", ...)
 * @param to   yeni durum
 */
public record SalesOrderStatusChanged(long orderId, String from, String to) {
}
