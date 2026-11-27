package com.olivamore.api.sales;

/** Satis faturasi iptal edildi; iptalle ayni islemde yayinlanir, muhasebe entegrasyonu islemden sonra dinler. */
public record SalesInvoiceCancelled(long invoiceId) {
}
