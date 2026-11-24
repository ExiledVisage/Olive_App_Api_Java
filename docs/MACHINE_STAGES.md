# Uretim asamalari: sikim, filtreleme, otomatik hesaplar

Durum (2026-11-24): 1-6 yapildi (yag KG, kronometre, sikim/ayiklama, filtreleme tankta, randiman); sunucuya kuruldu.
Randiman: `yield` uzantisi (seed: Randiman Tespiti); `GET /api/batch-processes/{id}/yield` hesap, tamamlamada
`yield_percent` + `calculated_yield_percent`, `deviation_ratio`, `filtered_oil_kg`, `olive_kg`.
Tankta filtreleme: on dinlenmeden hemen sonraki kronometreli asama; `POST /api/storage-units/{id}/filtering/start`
baslatir, Boşalt bitirir ve asamayi kapatir; filtreleme surerken tanka giris yok; `GET .../resting-batches`.
Kalan cikti: material_transformation uzantisinin `remainder_product` ayari (seed: pomace); sunucu ciktiya
`calculated_quantity` ve `deviation_ratio` yazar.

## Bugun ne var (seed hatti)

| Sira | Asama | Ne yapar |
|---|---|---|
| 1 | Kantarda Tartim | zeytin materyali yaratilir |
| 2 | Ayiklama | "Ayiklanan Yabanci Madde (kg)" alani |
| 3 | Ikinci Tartim | zeytin miktari dusurulur (kalan zeytin) |
| 4 | Sikim Unitesine Transfer | veri yok |
| 5 | Sikim (`uses_machine`) | material_transformation: zeytin tuketilir, yag (LITRE) + pirina (KG) yaratilir; malaksasyon suresi/sicakligi |
| 6 | Zeytinyagi Tartimi | weighing: sikimda yaratilan yagi yalniz dusurebilir |
| 7 | Pirina Tartimi | weighing: pirinayi yalniz dusurebilir |
| 8 | Asitlik Analizi | analiz |
| 9 | Ön Dinlenme | pre_settling: yag dinlenme tankina girer, asama Boşalt'a kadar `resting` |
| 10 | Filtreleme (`uses_machine`) | yalniz "Filtre Tipi" alani |
| 11 | Randiman Tespiti | elle "Randiman %" |

Sorun: sikimda yag ve pirina miktari zorunlu ama gercek tartim 6 ve 7'de; ayni bilgi iki kez girilir, ilki tahmin.
Filtreleme parti parti ayri asama ama fiziksel olarak Boşalt'ta (dinlenme tanki -> filtre -> son tank) olur;
Boşalt'tan sonra yaglar son tankta karismistir, parti basina filtrelenmis yag olculemez.

## Kararlar (kullanici)

1. **Zeytinyagi kilo ile tutulur** (fabrika standardi). Gemlik/Ayvalik zeytinyagi tiplerinin birimi KG olur.
   Cevrim zaten ortak (`UnitConversion`, yogunluk 0.916 kg/L): tanklar litre kalabilir, dolum litre gosterir.
2. **Ayiklama:** veri girisi yok, yalniz surec asamasi. Kilo alani kalkar. Fire = 1. tartim - 2. tartim, hesaplanir.
3. **Kalan zeytin** = 2. tartimin miktari; randiman ve pirina hesabi bunun uzerinden. Gruplarda grubun kilosu.
4. **Sikim: Baslat / Bitir.**
   - Baslat: sure sayaci, alanlar kilitli.
   - Bitir: baslangic ve bitis saati gorunur, kullanici duzeltebilir; alanlar acilir.
   - Bitir asamayi tamamlamaz ("makine bitti, veri bekleniyor"); veriler kaydedilince asama tamamlanir.
   - Girdi: kalan zeytin otomatik. Zeytinyagi (kg) elle. Pirina (kg) = kalan zeytin - yag, otomatik dolar.
   - Malaksasyon suresi/sicakligi kalir. Kaydet yag ve pirina materyallerini bu miktarlarla yaratir.
5. **Zeytinyagi Tartimi (6) ve Pirina Tartimi (7) kalkar.** Pirina parti basina tartilmiyor (ortak havuz).
6. **Filtreleme Boşalt'a tasinir**, ayri Filtreleme asamasi kalkar. Bosaltma her zaman filtreden gecer.
   - Baslat / Bitir: filtreleme suresi.
   - Hangi son tanka (gerekirse birden cok) kac kg gittigi yazilir.
   - Fire = dinlenme tankina giren - son tanka giden (tortu + filtre copu tek kalem; filtre oncesi olcum yok,
     gerek de yok: tanka giren zaten kayitli).
   - Fire partilere tanktaki kutle paylarina gore dagitilir.
7. **Randiman Tespiti** en son, Boşalt'tan sonra acilir: parti payinin son tanka giden kismi / kalan zeytin x 100.
8. **Otomatik doldurulan kutular** (pirina, randiman):
   - Kutu hesaplanan degerle dolu gelir; hesaplanan deger yaninda hep gorunur.
   - Kullanici degistirebilir; "Geri al" hesaplanana dondurur.
   - Degistirilirse hesaplanan deger ve sapma orani ((girilen - hesaplanan) / hesaplanan) saklanir; sonraki
     hesaplar girilen degeri esas alir.
9. **Baslat / Bitir** Sikim ve Filtreleme'de (bkz. 12-13: kronometre eklentisi).

10. **Filtreleme asamasi kalir ama isi tank yapar** (2026-11-22): asamada on dinlenme tankina link; tank sayfasinda
    tanka bagli butun gruplar listelenir; Boşalt bagli butun gruplarin Filtreleme asamasini birlikte kapatir.
    Randiman Tespiti Filtreleme kapaninca acilir (bugunku `resting` beklemesi gibi).
11. **Eski kayitlar:** dev DB sifirlanir, fresh seed; eski sikim kayitlari icin gecis yok.

## Litre taramasi (2026-11-22)

Cevrim ortak oldugu icin yag KG'ye gecisi kucuk:

- `master-data.sql`: Gemlik/Ayvalik zeytinyagi tipleri (id 3, 4) birimi LITRE -> KG.
- `DemoFactory`: acilis stogu `"unit", "LITRE"` ve "litre" varsayan yorumlar/hesaplar (dolum sise adedi).
- Testler: `LITRE` gecen 8 IT dosyasi (FinalTankRules 13, TankRules 6, MaterialLineage 5, FillingRules 4,
  PreSettlingRules 3, StorageMovementConcurrency 2, BatchStageRules 1, BatchFlow 1) ve 6 senaryo kaydi; calistirinca
  bozulanlar guncellenir, senaryolar yeniden kaydedilir.
- Dokunulmayacaklar: tank kapasitesi (KG ya da LITRE secilebilir), sise hacmi (`volume_litre`), satis analitigi
  litresi (sise x hacim), migration'lardaki `KG/LITRE` kontrolleri.
- Flutter: birim gosterimi geneldir (`unitLabel`, `formatTankQuantity`, tank L/kg dugmesi); yag icin sabit litre
  varsayimi bulunmadi.

12. **Kronometre asamaya kaydolur** (2026-11-22): asama kaydina makine baslangici ve makine bitisi alanlari; asamanin
    kendi `started_at` / `completed_at` (kayit zamani) ayri kalir. Boşalt ayni zamanlari bagli her grubun Filtreleme
    asamasina yazar.
13. **Kronometre bir asama eklentisi** (`stage_extension_types` yeni tip), `uses_machine`'e baglanmaz: `uses_machine`
    grup bolme noktasi olarak kalir, iki anlam karismaz. Seed'de Sikim ve Filtreleme'ye eklenir; asama yonetiminden
    baska asamalara da eklenebilir.

Acik konu kalmadi.
