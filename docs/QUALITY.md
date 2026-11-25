# Kalite

Durum (2026-11-25): API (5c00cea) ve Flutter (e918cca) tamam; sunucu sifirlanip kuruldu, APK 1.0.39.
Kullanici: GreenOps'taki kalite tarafinin dordu de (laboratuvar analizi, dolum kontrolu, geri cagirma, BRC/IFS
hazirligi). GreenOps'tan kod alinmaz; GreenOps'ta geri cagirma, HACCP ve laboratuvar testleri yalnizca arka uc
taslagi, dolum kontrolu ve BRC izlenebilirlik paketi gercek (inceleme 2026-11-25).

## 1. Laboratuvar analizi (kullanici yolu)

Analizler zaten urunun ozelligi: yeni tablo ya da ayri analiz kaydi yok. Zeytinyagina yeni attribute'lar tanimlanir;
var olan analiz asamasinin formu (urune tanimli attribute'lardan olusur) bunlari asitligin yaninda kendiliginden
gosterir; degerler materyale yazilir, tank, dolum, izlenebilirlik ve karsilastirma ayni yerden okur.

Yeni attribute'lar (zeytinyagi):

| Anahtar | Etiket | Tip | Birim |
|---|---|---|---|
| `peroxide` | Peroksit | decimal | meq O2/kg |
| `k232` | K232 | decimal | |
| `k270` | K270 | decimal | |
| `delta_k` | Delta K | decimal | |
| `faee` | Yag asidi etil esterleri | decimal | mg/kg |
| `defect_median` | Kusur medyani (Md) | decimal | |
| `fruity_median` | Meyvemsilik medyani (Mf) | decimal | |
| `oil_class` | Yag sinifi | string | |

- Asama: "Asitlik Analizi" -> "Laboratuvar Analizi"; numune no'nun yanina laboratuvar adi alani.
- **Yag sinifi** (`OliveOilGrade`): Turk Gida Kodeksi Zeytinyagi ve Pirina Yagi Tebligi (2017/26; Ek-1 ve Ek-2
  RG 8/4/2023-32157 ile degisik) natürel yaglar:

| Sinif (`oil_class`) | Asitlik | Peroksit | K232 | K270 | Delta K | FAEE | Duyusal |
|---|---|---|---|---|---|---|---|
| `extra_virgin` natürel sizma | <= 0,8 | <= 20 | <= 2,50 | <= 0,22 | <= 0,01 | <= 35 | Md = 0, Mf > 0 |
| `virgin` natürel birinci | <= 2,0 | <= 20 | <= 2,60 | <= 0,25 | <= 0,01 | - | Md <= 3,5, Mf > 0 |
| `lampante` ham (rafinajlik) | digerleri | | | | | | Md > 3,5 ya da Mf = 0 |

  - En kotu deger belirler; girilmeyen deger sinifi dusurmez, "eksik" diye listelenir (asitlik yoksa sinif yok).
  - Analiz kaydedilirken istekte `oil_class` yoksa materyalin guncel degerlerinden hesaplanip yazilir; istekte
    varsa elle verilen kalir (sorumlu degistirir).
  - On izleme: `POST /api/olive-oil-grade` (degerler -> sinif, sinifi dusuren degerler ve sinirlari, eksikler);
    form canli gosterir.
- Karsilastirma ve analiz: uretim ozelliklerine peroksit, K232 ve sizma orani.

## 2. Dolum kontrolu (GreenOps OLV-TLM-DA-01-F01 karsiligi)

Dolum kosusuna bagli; imza yerine kaydi giren kullanici.

- **Hat hazirligi** (`filling_setups`, kosu basina bir): bant temiz, basincli hava kontrol edildi, onceki urun
  hattan kaldirildi; uc onay verilince onaylayan ve zaman. Saatlik kontrolden once gerekir.
- **Saatlik kontrol** (`filling_checks`): kontrol zamani; 5 numune agirligi (g), hedef ve tolerans (g), ortalama ve
  uygunluk (sunucu hesaplar: |ortalama - hedef| <= tolerans); sizdirmazlik, kapak torku, etiket, kodlama
  (lot/tarih) uygun / degil / bakilmadi; cam kirigi ve notu (kirik varsa not zorunlu); not.
  - Hedef: urunun hacmi x yag yogunlugu (0,916) x 1000 g; tolerans varsayilani hedefin %1,5'i (en az 1 g); ikisi de
    degistirilebilir.
  - Kirik sise fire kaydi (ambalaj fire) zaten kosuda var; kontrol ona dokunmaz, not dusulur.
- Kosu sayfasinda ozet: kontrol sayisi, uygun olmayan saatler; tamamlanan kosuda kontrol yoksa uyari.

## 3. Geri cagirma ve izlenebilirlik tatbikati

`recalls` + `recall_orders`. Tatbikat (BRCGS 3.9: yilda en az bir, alti ayda bir onerilir) ayni kayit, `drill`.

- Kaynak: teslimat (parti), tank materyali ya da dolum kosusu; sebep; tur (`recall` / `drill`).
- **Ileri iz** (olusturulurken hesaplanir, kayda yazilir; yeniden hesaplanabilir):
  - Teslimat -> gruplari -> materyalleri ve torunlari -> girdigi tank materyalleri (ve aktarmalarla diger tanklar)
    -> o tanklardan **giristen sonra** cekilen dolumlar -> siparisler.
  - Dolum -> konuldugu raflar -> o raftan cikan siparisler. Raflar kosu bazinda tutulmadigi icin: raf bosalana
    kadar o raftan cikan siparisler; raf yalnizca bu kosuyla doluysa **kesin**, degilse **olasi**. Raflar arasi
    aktarma izlenir.
- Siparis satiri: musteri, iletisim, adet, kesin / olasi; aranma zamani, geri gelen adet, not.
- Durum: acik -> tamamlandi / iptal. Tatbikatta sure (baslangic -> tamamlanma) hedef 4 saat; sonuc gecti / kaldi
  kendiliginden, bulgular yazilir.

## 4. BRC/IFS hazirligi

- **Dokuman kaydi** (`quality_documents`): kod, ad, tur (prosedur, talimat, form, plan, politika), standart maddesi,
  surum, sorumlu, son gozden gecirme, gozden gecirme sikligi (ay) -> sonraki tarih, durum (taslak / yururlukte /
  kaldirildi), baglanti, not. "Gozden gecirildi" islemi tarihi ilerletir.
- **Hazirlik paneli** (`GET /api/quality/readiness`): dokumanlar (geciken, 30 gunde gelecek), son tatbikat ve
  durumu (hic yok / 180 gunu gecti / 90 gunu gecti / uygun), acik geri cagirmalar, donemdeki zeytinyagi
  partilerinde sinifi belli olan orani, tamamlanan dolumlarda kontrollu olan orani ve uygun olmayan saat sayisi,
  bitmis urun kutle denkligi (dolan - cikan - stok farki).
- Demo veri: temel BRCGS dokuman listesi (HACCP plani, izlenebilirlik, geri cagirma, cam ve kirilgan madde,
  hasere, temizlik, kalibrasyon, ic denetim, tedarikci onayi, sikayet, egitim ...).

## Yetkiler ve modul

- Yeni modul `quality` (`com.olivamore.api.quality`), tablolar `db/migration/quality`. Diger modullerin tablolarini
  yalnizca okur (analiz gibi). Yag sinifi hesabi `product` modulunde (uretim ve kalite kullanir).
- Yetkiler: `quality.view` (59), `quality.manage` (60; dokuman, geri cagirma), `filling_check.record` (61).
  Gida muhendisi hepsi, depo operatoru 61, planlamaci 59.

## API

| Uc | Yetki |
|---|---|
| `POST /api/olive-oil-grade` (`values`) | `batch_process.view` |
| `GET /api/finished-product-runs/{id}/filling-checks` | kosu gorme, `filling_check.record` ya da `quality.view` |
| `PUT /api/finished-product-runs/{id}/filling-setup`, `POST .../filling-checks` | `filling_check.record` |
| `DELETE /api/filling-checks/{id}` | `quality.manage` |
| `GET /api/quality/readiness` | `quality.view` |
| `GET /api/quality/recalls`, `GET .../{id}`, `GET /api/quality/recall-sources?type&q` | `quality.view` |
| `POST /api/quality/recalls`, `POST .../{id}/retrace`, `/complete`, `/cancel`, `PATCH /api/quality/recall-orders/{id}` | `quality.manage` |
| `GET /api/quality/documents?status&review_state`, `GET .../{id}` | `quality.view` |
| `POST`, `PATCH`, `DELETE /api/quality/documents/{id}`, `POST .../{id}/review` | `quality.manage` |

Kod: `OliveOilGrade` (product), analiz asamasi `AnalysisBatchProcessService`; `quality` modulu: `FillingChecks`,
`Recalls`, `RecallTrace`, `RecallSources`, `QualityDocuments`, `Readiness`. Karsilastirmada `avg_peroxide`,
`avg_k232`, `extra_virgin_pct` (`ProductionData`). Demo: laboratuvar degerleri, dolum kontrolleri, dokuman listesi.
Test: `QualityIT`.

## Flutter

- Analiz asamasi formunda zeytinyagi icin sinif on izlemesi (`oil_grade_preview.dart`), yag sinifi secimi
  (Otomatik / elle), "Butun olcumleri ekle".
- Dolum kosusu sayfasinda hat hazirligi ve saatlik kontroller (`filling_check_section.dart`); silme uzun basma.
- Menu > Raporlar > "Kalite" (`quality_page.dart`): Hazirlik, Geri cagirma (olustur, iz, siparis satirlari,
  tamamla), Dokumanlar (ekle, duzenle, gozden gecirildi).

## Sonra

- Dolum kontrol formunun PDF'i; deger degisikliklerinin gecmisi; HACCP KKN kayitlari; uygunsuzluk / DOF.
