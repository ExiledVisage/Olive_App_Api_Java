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
| 4 | Satis faturasi kaydi (bizde) | Siparisten fatura; numara, KDV, cari |
| 5 | Parasut: cari, satis faturasi, e-fatura/e-arsiv | Parasut uygulama kaydi (OAuth) |
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
- Henuz yok: Invoiced + fatura no (fatura kaydi ve Parasut'le), fiyat gonderimi (satis fiyati alani ve kimin
  yonettigi karari), kargo etiketi, bizde iptal edilen siparisin Trendyol'a "tedarik edilemedi" bildirimi.

## 3. Hepsiburada

Durum (2026-11-27): yazildi, sahte Hepsiburada API'siyle test edildi; gercek hesap bekleniyor. Uclar ve alan adlari
resmi belgeden degil, acik kaynak bir istemciden (zanaat-dev/lonca) alindi; ilk gercek denemede dogrulanacak.

- Sunucular: ilan ve stok `https://listing-external.hepsiburada.com`, siparis ve paket
  `https://oms-external.hepsiburada.com` (test: `-sit`). Basic auth API kullanici adi/sifresi, User-Agent entegrator
  adi, her istekte `x-correlationid`. Magaza ID yolda: ilanda `merchantid`, sipariste `merchantId`. Baglantida API
  adresi verilirse butun isler o adrese gider (test).
- Ilanlar: `GET /listings/merchantid/{id}?offset&limit` (`listings[]`, `totalCount`). Urun id'si Hepsiburada SKU'su,
  eslestirme bizim SKU'yla (`merchantSku`); ilanda urun adi ve barkod yok. HB deposundaki ilan (`isFulfilledByHB`)
  alinmaz: stogunu Hepsiburada tutar.
- Siparis: `GET /orders/merchantId/{id}?offset&limit` (offset/limit zorunlu, en fazla 100) odemesi tamamlanmis,
  paketlenmemis kalemleri kalem kalem doner (`items[]`: `id` kalem, `orderNumber`, `sku`, `merchantSKU`, `quantity`,
  `orderDate`); ayni siparis numarasi tek siparis olur. Alici ve adres kalemdeki `shippingAddress`'ten (il `city`, ilce
  `town`, mahalle `district`); bu alanlar dogrulanmadi, yoksa bos gecilir. Saat dilimsiz tarih Turkiye saati.
- Stok: `POST /listings/merchantid/{id}/stock-uploads` `[{hepsiburadaSku, availableStock}]` (1000 kalem), donen `id`
  ile `GET .../stock-uploads/id/{id}` (`status` Ready/InProgress/Done, `errors[]` SKU ve gerekce).
- Hazirlaniyor: siparis onaylaninca kalemler paketlenir, `POST /packages/merchantId/{id}`
  `{"lineItemRequests":[{id, quantity}], "parcelQuantity":1}`; Trendyol'daki gibi hata kayda yazilir, yeniden denenir.
- Henuz yok: fatura linki/numarasi, kargo etiketi, fiyat, test (SIT) sunucularini secme.

## 5. Parasut

- OAuth2 (uygulama kaydi; firma id). Cari (contact), urun (product), satis faturasi (sales_invoice), e-fatura /
  e-arsiv resmilestirme.

## Kullanicidan istenecekler

- WooCommerce: site adresi ve okuma/yazma REST anahtari; tercihen deneme (staging) sitesi.
- Trendyol: satici id, API anahtari ve sifresi (Satici paneli > Hesap > Entegrasyon bilgileri).
- Hepsiburada: magaza (merchant) id, API kullanici adi ve sifresi, entegrator adi (Satici paneli > Ayarlar >
  Entegrasyonlar).
- Parasut: firma id; uygulama kaydi icin destekle yazisma.
- Urunlerin barkodlari (pazar yerinde kullanilan).
