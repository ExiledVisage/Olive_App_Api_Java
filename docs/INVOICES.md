# Satis faturasi

Durum (2026-11-27): bizdeki fatura kaydi yazildi (API ve Flutter); Parasut'e gonderim sirada
(docs/INTEGRATIONS.md asama 5). Fatura su an yalniz bizde bir kayit: resmi e-fatura / e-arsiv degil.

## Fiyat ve KDV

- **Urun:** `finished_products.sale_price` KDV dahil liste fiyati (bos olabilir), `vat_rate` KDV yuzdesi
  (0, 1, 10, 20; varsayilan 1). Zeytinyagi icin %1 varsayildi; oran muhasebeciyle dogrulanmali.
- **Siparis kalemi:** `order_items.unit_price` KDV dahil, indirim sonrasi birim fiyat; `vat_rate` kalem yazilirken
  urunden alinir (sonradan urunun orani degisse de kalem degismez).
  - Elle girilen sipariste fiyat verilmezse urunun o anki liste fiyati yazilir.
  - Site ve pazar yeri siparisi kendi satis fiyatiyla gelir: WooCommerce `(total + total_tax) / adet`, Trendyol
    kalemdeki `price`, Hepsiburada `totalPrice.amount / adet` (yoksa `unitPrice.amount`). Platform fiyat vermezse
    liste fiyati. Ayni urunun birden cok kalemi tek kaleme birlesir (fiyat agirlikli ortalama).
- Siparis cevabinda kalem basina `unit_price`, `vat_rate`, `line_total`; sipariste `total` (fiyatsiz kalem varsa bos)
  ve gecerli fatura (`invoice`: id, numara, tarih, tutar).
- Fiyatlari pazar yeri panelinden mi bizden mi yonetecegimiz karari acik; pazar yerine fiyat gonderimi yok.

## Fatura

- `POST /api/orders/{id}/invoice` (`invoice.manage`), govde istege bagli: `issue_date`, `note`. Onaylanmis, iptal
  edilmemis siparise kesilir (confirmed..shipped). Siparis basina tek gecerli (`issued`) fatura.
- Kalem fiyati yoksa urunun liste fiyati kullanilir; o da yoksa fatura kesilmez ("Fiyati olmayan urun: ...").
- **Tutarlar:** kalem tutari = birim fiyat x adet (KDV dahil); net = kalem tutari / (1 + oran/100), kurusa yuvarlanir;
  KDV = kalem tutari - net. Toplamlar kalemlerin toplami; genel toplam musterinin odedigine esit. Detayda oran
  basina dokum (`vat_breakdown`).
- **Numara:** `olive.invoice.prefix` (varsayilan OLV) + yil + 9 hane sira, ornek `OLV2026000000001`; e-arsiv numara
  bicimiyle ayni uzunluk. Sira yil icinde artar, iptal edilen numara tekrar kullanilmaz. Ayni anda kesilen faturalar
  transaction kilidiyle sirayla numara alir. Parasut'e gecince resmi numarayi Parasut verecek; bu numara bizdeki kayit
  numarasi olarak kalir.
- **Alici:** kesildigi andaki hali saklanir: unvan (firma adi, yoksa ad), VKN/TCKN, vergi dairesi, teslimat adresi,
  e-posta, telefon. Musteri sonradan degisse de fatura degismez. Musteride `tax_number` (10 hane VKN ya da 11 hane
  TCKN) ve `tax_office` alanlari var; bos olabilir (bireysel, e-arsiv).
- **Kilit:** faturasi kesilmis siparis duzenlenemez ve iptal edilemez; once fatura iptal edilir.
- **Iptal:** `POST /api/invoices/{id}/cancel` `{reason}` (zorunlu). Fatura numarasiyla `cancelled` kalir; siparise
  yeniden fatura kesilebilir (yeni numara).
- Liste `GET /api/invoices?status&customer_id&order_id` (sayfali, en yeni once, kalemsiz), detay
  `GET /api/invoices/{id}` (`invoice.view`).
- Tablolar: `invoices`, `invoice_lines` (migration/sales V11_07, V11_08). Izinler 67 `invoice.view`,
  68 `invoice.manage` (yalniz admin).

## Flutter

- Urun formu: satis fiyati (KDV dahil) ve KDV orani.
- Musteri: VKN/TCKN ve vergi dairesi; musteri duzenleme (ad, firma, telefon, e-posta, vergi bilgisi).
- Yeni siparis: kalemde birim fiyat, urun secilince liste fiyatiyla dolar.
- Siparis detayi: kalem fiyati ve KDV orani, sipariste tutar; "Fatura kes" (onaylanmis siparis, `invoice.manage`),
  kesilmis faturanin karti. Fatura ekrani: alici, kalemler, KDV dokumu, iptal.

## Sirada

- Parasut: cari, urun, satis faturasi, e-fatura / e-arsiv resmilestirme; resmi numara ve PDF.
- Pazar yerine fatura: Trendyol "Invoiced" + fatura numarasi/linki, Hepsiburada fatura linki.
- Fatura listesi ekrani (Flutter), tarih araligi ve musteri filtresi.
- Kargo ucreti ve siparis indirimi faturada ayri kalem degil (pazar yeri kalem fiyati indirim sonrasidir).
