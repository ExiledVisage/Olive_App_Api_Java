# Dis yazilim entegrasyonlari

Durum (2026-11-27): plan; kod yok. Kullanici entegrasyonlara gecmek istedi.

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
| 2 | Trendyol: siparis bize, stok (ve istenirse fiyat) | Satici id, API anahtari/sifresi; test ortami IP izni |
| 3 | Hepsiburada: siparis bize, stok | Merchant id + entegrasyon kullanicisi (Hepsiburada onayi) |
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

## 1. WooCommerce

- REST API v3, tuketici anahtari/sifresi (Basic auth, HTTPS sart).
- Siparis: `processing` durumundakiler cekilir; kalemler SKU/barkodla eslenir. Bizde sevk edilince siteye
  `completed` yazilir (istege bagli).
- Stok: urun ya da varyantin `stock_quantity` alani bizim satilabilir stokla guncellenir.

## 2. Trendyol

- Satici API'si (Basic auth: API anahtari/sifresi, User-Agent: "<satici id> - SelfIntegration").
- Siparis: paketler (`shipment-packages`) cekilir; bizde hazirlaninca durum Trendyol'a bildirilir (Picking,
  Invoiced). Kargo etiketi ve fatura linki sonraki adim.
- Stok ve fiyat: `price-and-inventory` toplu gonderim; islem sonucu ayrica sorgulanir.
- Test ortami (stage) satici IP izni ister: sunucu IP'si 161.35.54.96 Trendyol'a bildirilir.

## 3. Hepsiburada

- Merchant API (siparis, stok/fiyat listeleme). Entegrasyon kullanicisi Hepsiburada'dan alinir.

## 5. Parasut

- OAuth2 (uygulama kaydi; firma id). Cari (contact), urun (product), satis faturasi (sales_invoice), e-fatura /
  e-arsiv resmilestirme.

## Kullanicidan istenecekler

- WooCommerce: site adresi ve okuma/yazma REST anahtari; tercihen deneme (staging) sitesi.
- Trendyol: satici id, API anahtari ve sifresi (Satici paneli > Hesap > Entegrasyon bilgileri).
- Hepsiburada: merchant id ve entegrasyon kullanicisi.
- Parasut: firma id; uygulama kaydi icin destekle yazisma.
- Urunlerin barkodlari (pazar yerinde kullanilan).
