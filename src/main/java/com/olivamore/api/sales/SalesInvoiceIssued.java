package com.olivamore.api.sales;

/**
 * Satis faturasi kesildi; faturayla ayni islemde yayinlanir. Muhasebe entegrasyonu (Parasut) islem tamamlandiktan
 * sonra dinler; dinleyicinin hatasi faturayi geri almaz (docs/INVOICES.md).
 */
public record SalesInvoiceIssued(long invoiceId) {
}
