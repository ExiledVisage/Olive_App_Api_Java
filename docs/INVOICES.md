# Satis faturasi

Durum (2026-11-27): bizdeki fatura kaydi ve Parasut'e gonderim yazildi (API ve Flutter; sahte Parasut sunucusuyla
test edildi, gercek hesap bekleniyor). Bizdeki fatura kendi basina resmi belge degil; resmilestirme Parasut'te
(e-fatura / e-arsiv).

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

## Parasut

Uclar resmi belgeden degil (devhub.parasut.com otomatik okumaya kapali), acik kaynak istemcilerden
(parasut-api-v4, netinternet/parasut-v4) ve bilinen API bicimlerinden; ilk gercek denemede dogrulanacak. En
belirsizler: kalem `unit_price`'in KDV haric oldugu, `e_invoice_inboxes` filtresi (`filter[vkn]`), e-fatura
govdesi (`scenario`, `to`), e-arsiv `internet_sale` alanlari, is durumlari ve resmi numaranin alani
(`active_e_document` icinde `invoice_number`).

- **Baglanti:** saglayici `parasut` (muhasebe; siparis, urun eslestirme ve stok yok). Erisim: firma ID, uygulama
  (client) ID ve gizli anahtari, kullanici e-postasi ve sifresi. Ayarlar: `invoice_push_enabled` (faturalari gonder),
  `e_document_enabled` (resmilestir; geri alinamaz, baslangicta kapali). API adresi verilirse (test) o kullanilir.
- **Giris:** `POST /oauth/token` sifre akisi (`grant_type=password`, `redirect_uri=urn:ietf:wg:oauth:2.0:oob`);
  isler `GET/POST /v4/{firma}/...`, JSON:API (`data.type`, `attributes`, `relationships`). Baglanti denemesi cari
  sayisini okur.
- **Gonderim:** fatura kesilince (`SalesInvoiceIssued`, islem bittikten sonra) her etkin Parasut baglantisina:
  1. Cari: daha once eslesmisse o; yoksa `filter[tax_number]`, sonra `filter[email]` ile aranir, bulunamazsa acilir
     (VKN 10 hane firma, degilse kisi; `account_type: customer`). Karsilik `integration_external_refs`'te.
  2. Urun: eslesmisse o; yoksa `filter[code]` (bizim SKU), sonra `filter[name]`, bulunamazsa acilir (KDV orani, KDV
     haric liste fiyati, `currency: TRL`).
  3. Satis faturasi: `item_type: invoice`, aciklama bizim numara, kesim ve vade tarihi, siparis no (pazar yeri
     numarasi ya da `#id`), alici vergi/adres bilgisi; kalemler KDV haric birim fiyat (net tutar / adet, 6 ondalik),
     KDV orani, urun iliskisi.
  4. Resmilestirme aciksa: VKN/TCKN `e_invoice_inboxes`'ta varsa e-fatura (`scenario: basic`, `to` posta kutusu),
     yoksa e-arsiv. Site ve pazar yeri siparisinde e-arsive internet satisi: adres (Trendyol/Hepsiburada sitesi, site
     icin baglanti adresi), odeme turu (pazar yerinde `ODEMEARACISI`, sitede `KREDIKARTI/BANKAKARTI`), platform ve
     odeme tarihi (siparis tarihi). Is (`trackable_jobs`) birkac kez sorgulanir; bitince resmi numara okunur.
- **Durum** (`integration_invoices`): pending, sent, formalizing, formalized, failed, cancelled. Hata gerekcesiyle
  kaydedilir; yeniden deneme kaldigi adimdan devam eder (olusmus fatura yeniden olusturulmaz, reddedilen
  resmilestirme bastan baslar). Zamanlayici yarim saatte bir dener; `POST .../invoices/retry` hemen, `POST
  .../invoices/{fatura}/send` tek faturayi.
- **Iptal:** bizde iptal edilen fatura Parasut'te `DELETE .../sales_invoices/{id}/cancel` ile iptal edilir;
  resmilesmis faturaya dokunulmaz, kayda "Parasut'ten iptal edin" yazilir.
- Uclar: `GET /api/integrations/connections/{id}/invoices` (son 200), `GET /api/integrations/invoices/{fatura}`
  (faturanin butun muhasebe baglantilarindaki durumu), gonder ve yeniden dene (`integration.manage`).
- Flutter: baglanti formunda Parasut (yonerge, "Faturalari gonder", "Resmilestir"), baglantida Ozet + Faturalar
  sekmesi (durum, resmi no, hata, yeniden gonder), fatura ekraninda Muhasebe karti.
- Yok: PDF, tahsilat (odeme) kaydi, iade faturasi, token yenileme (her is yeni token alir), resmilesmis faturanin
  iptali.

## Sirada

- Pazar yerine fatura: Trendyol "Invoiced" + fatura numarasi/linki, Hepsiburada fatura linki.
- Fatura listesi ekrani (Flutter), tarih araligi ve musteri filtresi.
- Kargo ucreti ve siparis indirimi faturada ayri kalem degil (pazar yeri kalem fiyati indirim sonrasidir).
