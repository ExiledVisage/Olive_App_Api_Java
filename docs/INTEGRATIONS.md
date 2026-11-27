# Dis yazilim entegrasyonlari

Durum (2026-11-27): asama 0 ve WooCommerce baglayicisi API'de yazildi (sahte WooCommerce sunucusuyla test edildi;
gercek site bilgisi henuz yok). Flutter Entegrasyonlar ekrani sirada.

Kullanicinin cevaplari:
- Satis kanallari: WooCommerce (kendi site), Trendyol, Hepsiburada.
- Muhasebe: Parasut (once); Logo ve Mikro da istenenler arasinda (sonra).
- Oncelik: siparisler bize dussun (elle girilmesin).

GreenOps'ta WooCommerce entegrasyonu var (HMAC'li webhook ile siparis); kod alinmaz, yalniz fikir.

## Asamalar

| Asama | Ne | On kosul |
|---|---|---|
| 0 | Ortak altyapi: modul, erisim bilgileri, urun eslestirme, kuyruk, Entegrasyonlar ekrani | - |
| 1 | WooCommerce: siparis bize, stok siteye | Site adresi + REST anahtari (okuma/yazma) |
| 2 | Trendyol: siparis bize, stok, hazirlaniyor bildirimi (fiyat sonra) — yazildi | Satici id, API anahtari/sifresi; test ortami IP izni |
| 3 | Hepsiburada: siparis bize, stok, paketleme — yazildi | Merchant id + entegrasyon kullanicisi (Hepsiburada onayi) |
| 4 | Satis faturasi kaydi (bizde): fiyat, KDV — yazildi (docs/INVOICES.md) | - |
| 5 | Parasut: cari, satis faturasi, e-fatura/e-arsiv — yazildi | Parasut uygulama kaydi (OAuth) |
| 6 | Logo / Mikro | Kurulum tipi (bulut / sirket ici) netlesince |

Neden bu sira: WooCommerce kendi siteniz, deneme magazasi kolay kurulur ve onay beklemez; ortak altyapi onunla
oturur. Trendyol ve Hepsiburada ayni kalibi kullanir. Muhasebe icin once bizde fatura kaydi gerekir.

## 0. Ortak altyapi

- **Modul `integration`:** saglayici basina baglayici; ortak isler: siparis cek, stok gonder, (fiyat gonder),
  fatura/cari gonder.
- **Baglantilar (`integration_connections`):** saglayici, ad, durum (aktif/duraklatildi), erisim bilgileri sifreli
  (sunucu anahtariyla, AES-GCM), son basarili senkron. Bilgiler uygulamadan girilir; cevapta maskeli doner.
- **Urun eslestirme:** bitmis urune `barcode` ve `sku`; `integration_product_links`: baglanti + bizim urun +
  saglayicinin urun/varyant id'si ve barkodu. Barkod tutuyorsa otomatik eslesir, tutmayan "eslesmemis" listesine
  duser.
- **Musteri:** pazar yeri siparisinde musteri adres bilgisiyle bizim musteri kaydina eslenir ya da yeni olusur
  (e-posta / telefon ile).
- **Gelen siparis:** mevcut siparis moduline duser; `source` (woocommerce/trendyol/hepsiburada), `external_id`,
  `external_status`. Ayni dis siparis iki kez gelmez (baglanti + dis id benzersiz). Eslesmeyen urunlu siparis
  "inceleme bekliyor" olur.
- **Giden isler (outbox):** stok/fiyat/fatura gonderimi kuyruga yazilir, artan araliklarla tekrar denenir
  (1 dk, 5 dk, 30 dk...); hepsi senkron gecmisinde. Stok degisince (dolum biter, sevk edilir) ilgili urun kuyruga.
- **Siparis cekme:** birkac dakikada bir sorgulama (alan adi gerekmez). WooCommerce/Trendyol anlik bildirimi
  (webhook) alan adi ve HTTPS gelince eklenir.
- **Uygulama:** Yonetim > Entegrasyonlar: baglantilar (durum, son senkron, "simdi esitle"), eslesmemis urunler,
  hatalar ve gecmis. Siparis kartinda kaynak rozeti (Trendyol, Site...).
- **Yetkiler:** `integration.view`, `integration.manage`.

## Yapilanlar (API)

Paketler (`com.olivamore.api.integration`): `provider` (platform listesi, ortak istemci arayuzu, `woocommerce/`),
`connection`, `catalog`, `order`, `stock`, `sync`. Testler `src/test/.../integration/` ve sahte site
`integration/support/FakeWooCommerce`.

| Is | Uc | Not |
|---|---|---|
| Barkod/SKU | `finished_products.barcode`, `sku` | benzersiz, demo urunlerde dolu |
| Baglanti | `/api/integrations/providers`, `/api/integrations/connections[/{id}]`, `.../check` | sifreli bilgi, maskeli cevap |
| Eslestirme | `.../catalog/refresh`, `.../catalog?status=`, `.../catalog/unlinked-products`, `PUT/DELETE /api/integrations/catalog-links/{id}` | barkod > SKU, elle, eslestirilmesin |
| Siparis | `.../orders/pull`, `.../orders/retry`, `.../orders?status=` | eslesen taslak siparis, eslesmeyen bekler |
| Stok | `.../stock`, `.../stock/push` | satilabilir = stok - acik siparis; yalniz degisen |
| Gecmis | `.../runs` | her calisma; hata baglantiya |
| Zamanlayici | `SyncScheduler` | 5 dk; `INTEGRATION_SCHEDULE_*` |

Siparislere `source` (manual/woocommerce/...) ve `external_ref` eklendi; kapi `sales.ExternalOrderIntake`.

## 1. WooCommerce

- REST API v3, tuketici anahtari/sifresi (Basic auth, HTTPS sart).
- Siparis: `processing` durumundakiler cekilir; kalemler SKU/barkodla eslenir. Bizde sevk edilince siteye
  `completed` yazilir (istege bagli).
- Stok: urun ya da varyantin `stock_quantity` alani bizim satilabilir stokla guncellenir.

## 2. Trendyol

Durum (2026-11-27): yazildi (9e27337), sahte Trendyol API'siyle test edildi; gercek hesap bekleniyor.

- Satici API'si `https://apigw.trendyol.com/integration` (test: `stageapigw`); Basic auth (API anahtari/sifresi),
  her istekte `storeFrontCode: TR`, User-Agent "<satici id> - SelfIntegration". Baglantida API adresi istege bagli.
- Urunler: `GET /product/sellers/{id}/products?approved=true` sayfa sayfa; Trendyol urunu ve stogu barkodla tanir,
  eslestirme anahtari barkod. Arsivlenmis urun alinmaz.
- Siparis: `GET /order/sellers/{id}/v2/orders?status=Created` (eski uc 15.10.2026'da kalkiyor), son degisime gore;
  aralik en fazla iki hafta. `orderDate` GMT+3 duvar saati milisaniyesi, UTC'ye cevrilir.
- Stok: `POST /inventory/sellers/{id}/products/price-and-inventory` (1000 kalem, urun basina en fazla 20.000);
  donen is numarasiyla `GET /product/sellers/{id}/products/batch-requests/{no}` sorgulanir, FAILED kalemin gerekcesi
  yazilir. Ayni istek 15 dk icinde tekrarlanmamali: yalniz degisen gonderildigi icin olmaz.
- Hazirlaniyor: siparis bizde onaylaninca (confirmed..ready) `PUT /order/sellers/{id}/shipment-packages/{paket}`
  `{"status":"Picking","lines":[{lineId,quantity}],"params":{}}`; kalem numaralari siparis alinirken saklanir.
  Satis modulu `SalesOrderStatusChanged` yayinlar, entegrasyon islem bittikten sonra bildirir (hata durum
  degisikligini geri almaz). Reddedilirse (musteri iptal etmis) gerekce `integration_orders.platform_error`'a
  yazilir; zamanlayici yarim saatte bir, "Simdi yeniden bildir" (`POST .../orders/report`) hemen dener.
- Faturalandi: fatura kesilince (resmilestirme varsa resmilesince) paket `Invoiced` + `params.invoiceNumber`, sonra
  resmi belgenin PDF'i `POST /sellers/{id}/seller-invoice-file` (multipart) ile yuklenir (docs/INVOICES.md).
- Kargo etiketi (resmi belgeden): paketin `cargoTrackingNumber`'i `GET /order/sellers/{id}/orders?shipmentPackageIds=`
  ile okunur; `POST /sellers/{id}/common-label/{takip}` `{"format":"ZPL","boxQuantity":1}` ile istenir,
  `GET` ile `data[].label` (ZPL metni) alinir. Trendyol'un anlasmali kargosu (TEX, Aras) icin; hazirlaniyor
  bildirilmis sipariste. Saklanmaz, her istekte taze. `GET /api/integrations/sales-orders/{siparis}/label`
  (`integration.view` ya da `order.update_status`).
- Tedarik edilemedi (resmi belgeden): pazar yeri siparisi bizde iptal edilince
  `PUT .../shipment-packages/{paket}/items/unsupplied` `{"lines":[{lineId,quantity}],"reasonId":500}` (500 stok
  tukendi; digerleri 501 kusurlu, 502 hatali fiyat, 504 entegrasyon hatasi, 505 toplu alim, 506 mucbir sebep).
  Trendyol paketi iptal eder (`platform_status = unsupplied`); hata kayda yazilir, yeniden denenir.
- Henuz yok: fiyat gonderimi (satis fiyati alani ve kimin yonettigi karari), tedarik edememe gerekcesini secme
  (hep 500).

## 3. Hepsiburada

Durum (2026-11-27): yazildi, sahte Hepsiburada API'siyle test edildi; gercek hesap bekleniyor. Siparis, paket,
etiket uclari ve siparis alanlari Hepsiburada'nin yayimladigi OMS taniminin bir kopyasindan (zanaat-dev/lonca
`specs/hepsiburada/oms-external.json`) dogrulandi; ilan ve stok uclarinin tanimi yayimlanmamis, acik kaynak
istemciden alindi.

- Sunucular: ilan ve stok `https://listing-external.hepsiburada.com`, siparis ve paket
  `https://oms-external.hepsiburada.com` (test: `-sit`). Basic auth API kullanici adi/sifresi, User-Agent entegrator
  adi, her istekte `x-correlationid`. Magaza ID yolda, kucuk harfle `merchantid`. Baglantida API adresi verilirse
  butun isler o adrese gider (test).
- Ilanlar: `GET /listings/merchantid/{id}?offset&limit` (`listings[]`, `totalCount`). Urun id'si Hepsiburada SKU'su,
  eslestirme bizim SKU'yla (`merchantSku`); ilanda urun adi ve barkod yok. HB deposundaki ilan (`isFulfilledByHB`)
  alinmaz: stogunu Hepsiburada tutar.
- Siparis: `GET /orders/merchantid/{id}?offset&limit` (offset/limit zorunlu, en fazla 100) odemesi tamamlanmis,
  paketlenmemis kalemleri kalem kalem doner (`items[]`: `id` kalem, `orderNumber`, `sku`, `merchantSKU`, `quantity`,
  `orderDate`); ayni siparis numarasi tek siparis olur. Alici ve adres kalemdeki `shippingAddress`'ten (il `city`, ilce
  `town`, mahalle `district`; tanimdan dogrulandi). Saat dilimsiz tarih Turkiye saati.
- Stok: `POST /listings/merchantid/{id}/stock-uploads` `[{hepsiburadaSku, availableStock}]` (1000 kalem), donen `id`
  ile `GET .../stock-uploads/id/{id}` (`status` Ready/InProgress/Done, `errors[]` SKU ve gerekce).
- Hazirlaniyor: siparis onaylaninca kalemler paketlenir, `POST /packages/merchantid/{id}`
  `{"lineItemRequests":[{id, quantity}], "parcelQuantity":1}`; cevaptaki `packageNumber` saklanir
  (`integration_orders.platform_ref`). Trendyol'daki gibi hata kayda yazilir, yeniden denenir.
- Kargo etiketi: `GET /packages/merchantid/{id}/packagenumber/{paket}` (kargo firmasi, `trackingInfoCode`) ve
  `.../labels?format=ZPL` (`data[]`, `format`). Etiket verisinin bicimi ve `format` degerleri tanimda yazmiyor;
  gercek hesapla dogrulanacak.
- Henuz yok: fatura linki (Hepsiburada yalniz en az 10 yil acik kalacak link kabul ediyor:
  `PUT /packages/merchantid/{id}/packagenumber/{paket}/invoice`, `invoiceLink`; Parasut'un PDF adresi gecici, bizim
  sunucunun alan adi yok), bizde iptal edilen siparisin bildirimi (`POST /lineitems/merchantid/{id}/id/{kalem}/
  cancelbymerchant` `{reasonId}` var ama gerekce kodlari belgelenmemis), fiyat, test (SIT) sunucularini secme.

## 5. Parasut

Durum (2026-11-27): yazildi, sahte Parasut sunucusuyla test edildi; gercek hesap bekleniyor. Ayrintilar
docs/INVOICES.md "Parasut": OAuth2 sifre akisi, cari ve urun bulma/acma, satis faturasi, e-fatura / e-arsiv
resmilestirme (is olarak), hata ve yeniden deneme, iptal.

## 6. Logo / Mikro

Durum (2026-11-27): yazilmadi; urun ve kurulum bilgisi bekleniyor. Muhasebe tarafi `AccountingClient` arayuzune
bagli (cari, urun, satis faturasi, resmilestirme); Logo ya da Mikro bu arayuzun yeni bir uygulamasi olarak eklenir,
fatura gonderimi, yeniden deneme ve ekranlar ortak.

- Logo: Tiger 3 / Tiger Enterprise 3 icin "Logo REST Service" (Logo Objects uzerinde; Logo Objects lisansi gerekir),
  j-Platform urunleri icin j-Platform REST (`/logo/rest/v1.0/...`, ornek cari `fiarpcardexchanges`). Satis faturasi
  ucunun ayrintisi acik belgede yok. Sirket ici kurulumda sunucumuzun Logo sunucusuna ag erisimi (VPN ya da disa
  acik, HTTPS'li adres) gerekir.
- Mikro: acik API belgesi bulunamadi; erisim urune (Run, Jump, Fly) ve lisansa bagli, Mikro'dan istenir.
- Istenecekler: hangi urun ve surum (Logo Tiger 3 / Go 3 / j-Platform; Mikro Jump / Fly), bulut mu sirket ici mi,
  REST servisinin adresi ve kullanicisi, varsa test firmasi; Logo/Mikro bayisinden API belgesi.

## Kullanicidan istenecekler

- WooCommerce: site adresi ve okuma/yazma REST anahtari; tercihen deneme (staging) sitesi.
- Trendyol: satici id, API anahtari ve sifresi (Satici paneli > Hesap > Entegrasyon bilgileri).
- Hepsiburada: magaza (merchant) id, API kullanici adi ve sifresi, entegrator adi (Satici paneli > Ayarlar >
  Entegrasyonlar).
- Parasut: firma id; uygulama kaydi icin destekle yazisma.
- Urunlerin barkodlari (pazar yerinde kullanilan).
