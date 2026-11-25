# Karsilastirma

Durum (2026-11-24): API ve Flutter tamam (11 grup); deploy kullanici istediginde.
Kullanici: analizde bir sekme; once ne karsilastirilacagi (tedarikci, tarla, musteri ...) secilir, sonra 2, 3 ya da
istenen sayida kalem ve secilen ozellikler (ya da hepsi) yan yana. Ilk surum yalnizca tedarikciydi; kullanici "her sey
karsilastirilabilsin" dedi.

## Gruplar

| Grup | Anahtar | Kalemler | Ozellikler | Kaynak |
|---|---|---|---|---|
| Tedarikciler | `supplier` | donemde getirisi olanlar | uretim | partiler, teslimatlar, IAS |
| Tarlalar | `field` | ayni | uretim | ayni |
| Zeytin cesitleri | `product_type` | ayni | uretim | ayni (partinin ilk girdi tipi) |
| Teslimatlar | `delivery` | ayni | uretim | ayni (gruplar teslimatina sayilir) |
| Alim kanallari | `channel` | ciftci / tuccar / kendi bahcemiz | uretim | ayni; grup yalnizca fiyat yetkisiyle |
| Uretim hatlari | `line` | ayni | uretim | ayni |
| Musteriler | `customer` | donemde siparisi olanlar | satis | iptal edilmemis siparis kalemleri |
| Bitmis urunler | `finished_product` | ayni | satis + dolum | siparis kalemleri, tamamlanan dolumlar |
| Tanklar | `tank` | butun tanklar | hareket, kalite, seviye | tank hareketleri (litreye cevrilir), bakiye |
| Makineler | `machine` | butun makineler | calisma, verim | makine calismalari ve partileri |
| Donemler | `period` | son 12 ay, 3 hasat, 3 yil | uretim + satis ozeti | ayni; tedarikci / tarla kapsamli olabilir |

Ozellikler (`CompareMetrics`):
- **Uretim**: getiris sayisi, gelen / sikilan zeytin, yag kg; randiman, brut randiman, pirina ve tortu orani; asitlik,
  IAS (zeytin yag orani, KPY, beklenen randiman, pirina KPY; parti kilosuyla agirlikli); kg fiyati (KDV haric/dahil),
  kg maliyeti, alim tutari, kanal (fiyat yetkisiyle); son getiris, ortalama getiris.
- **Musteri**: siparis sayisi, litre, sise, ortalama siparis, urun cesitliligi, siparis araligi (gun), en cok aldigi
  urun, son siparis. Siparislerde fiyat olmadigi icin tutar yok.
- **Bitmis urun**: satilan litre ve adet, siparis ve musteri sayisi, siparis basina adet, dolumu biten adet, en cok alan
  musteri, son siparis.
- **Tank**: giren, cikan, net, aktarmayla giren / cikan (litre; kilo hareketler materyalin yogunluguyla), hareket
  sayisi, giren yagin asitligi (miktarla agirlikli), su anki miktar ve doluluk (donemden bagimsiz, seride yok).
- **Makine**: calisma sayisi, islenen parti, calisma suresi, ortalama calisma; islenen zeytin (partilerin sikilan
  kilosu), saatlik islenen, kapasite kullanimi (makine kapasitesine gore), randiman, asitlik, son calisma.
- **Donem**: uretim ozellikleri + siparis sayisi, satilan litre ve sise, musteri sayisi. Tedarikci ya da tarla kapsami
  verilirse yalnizca o uretim, satis ozeti bos.

Her sayisal ozellikte "yuksek iyi", "dusuk iyi" ya da yon yok; yonu olanlarda en iyi ve en kotu isaretlenir (esitler
birlikte, en az iki degerli kalem gerekir). Hareket miktarlari gibi iyi/kotu olmayanlar isaretlenmez.

## API (yetki `analytics.view`)

- `GET /api/analytics/compare`: gruplar (`key`, `label`, `range`: donem secilir mi, `product_type`).
- `GET /api/analytics/compare/{grup}/metrics`: ozellikler (`key`, `label`, `unit`, `group`, `higher_is_better`,
  `price`, `series`); fiyat yetkisi yoksa maliyet ve kanal yok.
- `GET /api/analytics/compare/{grup}/options?from&to`: secici kalemleri (`id`, `name`, `detail`), en buyukten.
- `GET /api/analytics/compare/{grup}?ids=a,b&metrics=&from&to&product_type_id&supplier_id&field_id&series=`:
  `ids` 2-50 (donemde `2026-10`, `2025/26`, `2026`); `metrics` verilmezse hepsi; `series` tek ozelligin aylik
  degerleri (donem grubunda yok). Cevap: `metrics[]`, `items[]` (`id`, `name`, `detail`, `values`, `best`,
  `worst`), `series` (`metric`, `periods`, `lines[]`: `item_id`, `values`).

Kod: `Comparison` (dogrulama, en iyi / en kotu, seri), `CompareSubject` (grup arayuzu), `ProductionSubject`,
`SalesSubject`, `FacilitySubject`, `PeriodSubject`, `ProductionData` (uretim degerleri; analizdeki
`AnalyticsQueries.batches` ve `AnalyticsService.totals` ile, tedarikci listesiyle ayni), `CompareQueries`.
Test: `CompareIT`.

## Flutter

Analiz > "Karsilastir" (`compare_tab.dart`):
1. Ustte grup cipleri; donem grubunda ustteki tarih secimi kullanilmaz (not gosterilir).
2. Kalem secici (arama, coklu secim, adin altinda ayrinti ve miktar), ozellik secici (gruplu, varsayilan hepsi).
3. 4 kaleme kadar yan yana tablo (ilk sutun sabit, en iyi yesil, en kotu turuncu); fazlasinda ozellik kartlari (en
   iyiden en kotuye cubuklar); elle gecis.
4. Aylik cizgi grafik (seride olabilen ozellikler); tedarikci adindan tedarikci sayfasi.

## Kararlar (2026-11-24)

1. Butun ozellik gruplari; kucuk secim siniri yok (50 performans siniri); sigmayinca kartlar.
2. Karsilastirma tedarikciyle sinirli kalmasin: tarla, cesit, teslimat, kanal, hat, musteri, bitmis urun, tank,
   makine, donem.
