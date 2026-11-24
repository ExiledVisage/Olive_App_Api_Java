# Tedarikci karsilastirma

Durum: plan (2026-11-24), kararlar alindi, kod yazilmadi. API ve Flutter birlikte. Kullanici: analiz bolumune bir sekme; 2, 3 ya da
istenen sayida tedarikci secilir, secilen ozellikler (ya da hepsi) yan yana karsilastirilir.

## Bugun ne var

- Analiz sekmeleri: Randiman, Tedarikciler, Musteriler, Raporlarim (rapor olusturucu).
- `GET /api/analytics/suppliers?from&to&product_type_id`: tedarikci basina toplamlar (`BatchTotals`: gelen,
  sikilan, yag, pirina, tortu kg; parti sayisi; randiman ve brut randiman; ortalama asitlik), gelen kg'ye gore sirali.
  Karsilastirma bunun uzerine kurulur; hesap yeniden yazilmaz.
- Tedarikci getirislerinde alim toplamlari (`/api/suppliers/{id}/deliveries`, fiyat yetkisiyle).
- IAS degerleri parti materyallerinde (zeytin / pirina: yag, nem, KPY, endustriyel randiman).

## Karsilastirilan ozellikler

| Grup | Ozellik | Kaynak |
|---|---|---|
| Hacim | Getiris (teslimat) sayisi, gelen zeytin kg, sikilan kg, uretilen yag kg | analitik toplamlar |
| Verim | Randiman %, brut randiman %, pirina ve tortu orani | analitik toplamlar |
| Kalite | Ort. asitlik, zeytin yag % ve KPY, pirina KPY, beklenen randiman | IAS (kg agirlikli ort.) |
| Maliyet | Ort. kg fiyati ve kg maliyeti (KDV haric/dahil), alim tutari | alim bilgisi (fiyat yetkisiyle) |
| Duzen | Son getiris, ortalama getiris kg'si, kanal (ciftci/tuccar/kendi bahcemiz) | teslimatlar |

Her ozellik icin "yuksek iyi" ya da "dusuk iyi" (ornek: randiman yuksek, asitlik ve kg maliyeti dusuk, pirina KPY
dusuk) tanimlidir; tablo en iyi ve en kotuyu buna gore isaretler. Degeri olmayan (olcum yok) bos gosterilir.

## API

`GET /api/analytics/suppliers/compare?ids=1,2,3&metrics=yield_pct,avg_acidity,...&from&to&product_type_id`
(`metrics` verilmezse hepsi; `ids` en az 2, en fazla 50 - yalnizca performans siniri; yetki `analytics.view`).

Cevap:
- `metrics[]`: anahtar, etiket, birim, grup, `higher_is_better`.
- `suppliers[]`: id, ad, kod; `values` (metrik -> deger ya da null), `best` / `worst` metrik listeleri.
- `series` (istege bagli `?series=yield_pct&interval=month`): secilen tek metrigin aylik degerleri, tedarikci basina
  (cizgi grafik icin).

Hesap: var olan analitik toplamlar (`AnalyticsQueries.batches` + `totals`) secilen tedarikcilerle suzulur; IAS
ortalamalari ve alim ortalamalari kg agirlikli eklenir. Fiyat metrikleri yetkisiz kullanicida katalogda da yoktur.

## Flutter

Analizde yeni "Karsilastir" sekmesi:
1. Tedarikci secici (arama, coklu secim); donem (mevcut tarih secici) ve istege bagli urun tipi.
2. Ozellik secici: gruplar halinde onay kutulari, "Tumu" (varsayilan hepsi).
3. Gorunum secilen sayiya gore degisir (kullanici: sigmazsa tasarim degissin, karta ya da grafige donsun):
   - 4 tedarikciye kadar **yan yana tablo**: sutunlar tedarikciler, satirlar ozellikler; her satirda en iyi yesil,
     en kotu turuncu; ilk sutun (ozellik adi) sabit, gerekirse yatay kayar.
   - Daha fazlasinda **ozellik kartlari**: her ozellik bir kart, secilen tedarikciler en iyiden en kotuye yatay
     cubuklarla siralanir (degeri yanda). Kullanici iki gorunum arasinda elle de gecebilir.
4. Secilen bir ozellik icin cubuk grafik; zaman icin aylik cizgi grafik (tedarikci basina bir cizgi).
5. Tedarikci adina dokununca tedarikci sayfasi.

## Adimlar

1. API: karsilastirma ucu, metrik tanimlari, IAS ve alim ortalamalari, aylik seri; testler (secim, yetki, en
   iyi/en kotu, olcumu olmayan tedarikci).
2. Flutter: sekme, seciciler, tablo, grafikler; testler.
3. Deploy (kullanici istediginde).

## Sonra (ayni yapiyla)

Karsilastirilan tur genisletilebilir: arazi, zeytin cesidi, makine (kullanim, kg/saat, randiman), musteri, urun.
Ilk surum yalnizca tedarikci.

## Kararlar (2026-11-24)

1. Ilk surumde butun ozellik gruplari.
2. Kucuk bir secim siniri yok; ekrana sigmayinca tablo yerine ozellik kartlari (cubuk siralama).
